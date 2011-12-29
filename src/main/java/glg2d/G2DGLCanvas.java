/**************************************************************************
   Copyright 2011 Brandon Borkholder

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 ***************************************************************************/

package glg2d;

import java.awt.Component;
import java.awt.Graphics;

import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCanvas;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLContext;
import javax.media.opengl.GLDrawableFactory;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLJPanel;
import javax.media.opengl.GLPbuffer;
import javax.media.opengl.Threading;
import javax.swing.JComponent;
import javax.swing.OverlayLayout;
import javax.swing.RepaintManager;

public class G2DGLCanvas extends JComponent {
  private static final long serialVersionUID = -471481443599019888L;

  protected GLAutoDrawable canvas;

  protected boolean drawGL = true;

  /**
   * @see #removeNotify()
   */
  protected GLPbuffer sideContext;

  protected GLEventListener g2dglListener;

  protected JComponent drawableComponent;

  public static GLCapabilities getDefaultCapabalities() {
    GLCapabilities caps = new GLCapabilities();
    caps.setRedBits(8);
    caps.setGreenBits(8);
    caps.setBlueBits(8);
    caps.setAlphaBits(8);
    caps.setDoubleBuffered(true);
    caps.setHardwareAccelerated(true);
    return caps;
  }

  public G2DGLCanvas() {
    this(getDefaultCapabalities());
  }

  public G2DGLCanvas(GLCapabilities capabilities) {
    canvas = createGLComponent(capabilities, null);

    /*
     * Set both canvas and drawableComponent to be the same size, but we never
     * draw the drawableComponent except into the canvas.
     */
    setLayout(new OverlayLayout(this));
    add((Component) canvas);
    
    RepaintManager.setCurrentManager(GLAwareRepaintManager.INSTANCE);
  }

  public G2DGLCanvas(JComponent drawableComponent) {
    this();
    setDrawableComponent(drawableComponent);
  }

  public G2DGLCanvas(GLCapabilities capabilities, JComponent drawableComponent) {
    this(capabilities);
    setDrawableComponent(drawableComponent);
  }

  /**
   * Creates a {@code Component} that is also a {@code GLAutoDrawable}. This is
   * where all the drawing takes place. The advantage of a GLCanvas is that it
   * is faster, but a GLJPanel is more portable. The component should also be
   * disabled so that it does not receive events that should be sent to the
   * {@code drawableComponent}.
   */
  protected GLAutoDrawable createGLComponent(GLCapabilities capabilities, GLContext shareWith) {
    GLJPanel canvas = new GLJPanel(capabilities, null, shareWith);
    canvas.setEnabled(false);
    return canvas;
  }

  /**
   * Returns {@code true} if the {@code drawableComonent} is drawn using OpenGL
   * libraries. If {@code false}, it is using normal Java2D drawing routines.
   */
  public boolean isGLDrawing() {
    return drawGL;
  }

  /**
   * Sets the drawing path, {@code true} for OpenGL, {@code false} for normal
   * Java2D.
   * 
   * @see #isGLDrawing()
   */
  public void setGLDrawing(boolean drawGL) {
    this.drawGL = drawGL;
    ((Component) canvas).setVisible(drawGL);
    repaint();
  }

  public void setDrawableComponent(JComponent component) {
    if (component == drawableComponent) {
      return;
    }

    if (g2dglListener != null) {
      canvas.removeGLEventListener(g2dglListener);
    }

    if (drawableComponent != null) {
      remove(drawableComponent);
    }

    drawableComponent = component;
    if (drawableComponent != null) {
      g2dglListener = createG2DListener(drawableComponent);
      canvas.addGLEventListener(g2dglListener);
      add(drawableComponent);
    }
  }

  /**
   * Creates the GLEventListener that will draw the given component to the
   * canvas.
   */
  protected GLEventListener createG2DListener(JComponent drawingComponent) {
    return new G2DGLEventListener(drawingComponent);
  }

  public JComponent getDrawableComponent() {
    return drawableComponent;
  }

  /**
   * Calling {@link GLCanvas#removeNotify()} destroys the GLContext. We could
   * mess with that internally, but this is slightly easier.
   * <p>
   * This method is particularly important for docking frameworks and moving the
   * panel from one window to another. This is simple for normal Swing
   * components, but GL contexts are destroyed when {@code removeNotify()} is
   * called.
   * </p>
   * <p>
   * Our workaround is to use context sharing. The pbuffer is initialized and by
   * drawing into it at least once, we automatically share all textures, etc.
   * with the new pbuffer. This pbuffer holds the data until we can initialize
   * our new JOGL canvas. We share the pbuffer canvas with the new JOGL canvas
   * and everything works nicely from then on.
   * </p>
   * <p>
   * This has the unfortunate side-effect of leaking memory. I'm not sure how to
   * fix this yet.
   * </p>
   */
  @Override
  public void removeNotify() {
    prepareSideContext();

    remove((Component) canvas);
    super.removeNotify();

    canvas = createGLComponent(sideContext.getChosenGLCapabilities(), sideContext.getContext());
    canvas.addGLEventListener(g2dglListener);
    add((Component) canvas, 0);
  }

  protected void prepareSideContext() {
    if (sideContext == null) {
      sideContext = GLDrawableFactory.getFactory().createGLPbuffer(canvas.getChosenGLCapabilities(), null, 1, 1, canvas.getContext());
      sideContext.addGLEventListener(g2dglListener);
    }

    Runnable work = new Runnable() {
      @Override
      public void run() {
        sideContext.display();
      }
    };

    if (Threading.isOpenGLThread()) {
      work.run();
    } else {
      Threading.invokeOnOpenGLThread(work);
    }
  }

  @Override
  public void paint(Graphics g) {
    if (drawGL) {
      canvas.display();
    } else {
      super.paint(g);
    }
  }

  @Override
  protected void paintChildren(Graphics g) {
    /*
     * Don't paint the drawableComponent. If we'd use a GLJPanel instead of a
     * GLCanvas, we'd have to paint it here.
     */
    if (!drawGL) {
      super.paintChildren(g);
    }
  }

  @Override
  protected void addImpl(Component comp, Object constraints, int index) {
    if (comp == canvas || comp == drawableComponent) {
      super.addImpl(comp, constraints, index);
    } else {
      throw new IllegalArgumentException("Do not add component to this. Add them to the object in getDrawableComponent()");
    }
  }
}
