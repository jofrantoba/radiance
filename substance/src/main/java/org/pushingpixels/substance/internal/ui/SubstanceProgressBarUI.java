/*
 * Copyright (c) 2005-2020 Radiance Kirill Grouchnikov. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  o Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  o Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  o Neither the name of the copyright holder nor the names of
 *    its contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.pushingpixels.substance.internal.ui;

import org.pushingpixels.neon.api.NeonCortex;
import org.pushingpixels.substance.api.ComponentState;
import org.pushingpixels.substance.api.SubstanceSlices.ComponentStateFacet;
import org.pushingpixels.substance.api.SubstanceSlices.Side;
import org.pushingpixels.substance.api.colorscheme.ColorSchemeSingleColorQuery;
import org.pushingpixels.substance.api.colorscheme.SubstanceColorScheme;
import org.pushingpixels.substance.api.painter.fill.FractionBasedFillPainter;
import org.pushingpixels.substance.api.painter.fill.SubstanceFillPainter;
import org.pushingpixels.substance.internal.AnimationConfigurationManager;
import org.pushingpixels.substance.internal.utils.*;
import org.pushingpixels.trident.api.Timeline;
import org.pushingpixels.trident.api.Timeline.RepeatBehavior;
import org.pushingpixels.trident.api.Timeline.TimelineState;
import org.pushingpixels.trident.api.callback.TimelineCallback;
import org.pushingpixels.trident.api.ease.Spline;
import org.pushingpixels.trident.api.swing.SwingComponentTimeline;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.util.EnumSet;
import java.util.Set;

/**
 * UI for progress bars in <b>Substance</b> look and feel.
 *
 * @author Kirill Grouchnikov
 */
public class SubstanceProgressBarUI extends BasicProgressBarUI {
    private static final ComponentState DETERMINATE_SELECTED = new ComponentState(
            "determinate enabled", new ComponentStateFacet[] { ComponentStateFacet.ENABLE,
            ComponentStateFacet.DETERMINATE, ComponentStateFacet.SELECTION },
            null);

    private static final ComponentState DETERMINATE_SELECTED_DISABLED = new ComponentState(
            "determinate disabled",
            new ComponentStateFacet[] { ComponentStateFacet.DETERMINATE,
                    ComponentStateFacet.SELECTION },
            new ComponentStateFacet[] { ComponentStateFacet.ENABLE });

    private static final ComponentState INDETERMINATE_SELECTED = new ComponentState(
            "indeterminate enabled",
            new ComponentStateFacet[] { ComponentStateFacet.ENABLE, ComponentStateFacet.SELECTION },
            new ComponentStateFacet[] { ComponentStateFacet.DETERMINATE });

    private static final ComponentState INDETERMINATE_SELECTED_DISABLED = new ComponentState(
            "indeterminate disabled", null,
            new ComponentStateFacet[] { ComponentStateFacet.DETERMINATE, ComponentStateFacet.ENABLE,
                    ComponentStateFacet.SELECTION });

    private static final SubstanceFillPainter progressFillPainter = new FractionBasedFillPainter(
            "Progress fill (internal)", new float[] { 0.0f, 0.5f, 1.0f },
            new ColorSchemeSingleColorQuery[] { ColorSchemeSingleColorQuery.EXTRALIGHT,
                    ColorSchemeSingleColorQuery.LIGHT, ColorSchemeSingleColorQuery.MID });

    private final class SubstanceChangeListener implements ChangeListener {
        public void stateChanged(ChangeEvent e) {
            SubstanceCoreUtilities.testComponentStateChangeThreadingViolation(progressBar);

            int currValue = progressBar.getValue();
            int span = progressBar.getMaximum() - progressBar.getMinimum();

            int barRectWidth = progressBar.getWidth() - 2 * margin;
            int barRectHeight = progressBar.getHeight() - 2 * margin;
            int totalPixels = (progressBar.getOrientation() == JProgressBar.HORIZONTAL)
                    ? barRectWidth : barRectHeight;
            // fix for defect 223 (min and max on the model are the
            // same).
            int pixelDelta = (span <= 0) ? 0 : (currValue - displayedValue) * totalPixels / span;

            if (displayTimeline != null) {
                displayTimeline.abort();
            }

            displayTimeline =
                    AnimationConfigurationManager.getInstance().timelineBuilder(progressBar)
                            .addPropertyToInterpolate(Timeline.<Integer>property("displayedValue")
                                    .from(displayedValue)
                                    .to(currValue)
                                    .setWith((obj, fieldName, value) -> {
                                        displayedValue = value;
                                        if (progressBar != null) {
                                            progressBar.repaint();
                                        }
                                    }))
                            .setEase(new Spline(0.4f))
                            .build();

            // Do not animate progress bars used in cell renderers
            // since in this case it will most probably be the
            // same progress bar used to display different
            // values for different cells. Also do not animate progress bars
            // that are not part of the UI tree.
            boolean isInCellRenderer = (SwingUtilities.getAncestorOfClass(CellRendererPane.class,
                    progressBar) != null);
            boolean hasParent = (progressBar.getParent() != null);
            if (hasParent && !isInCellRenderer && Math.abs(pixelDelta) > 5) {
                displayTimeline.play();
            } else {
                displayedValue = currValue;
                progressBar.repaint();
            }
        }
    }

    /**
     * Hash for computed stripe images.
     */
    private static LazyResettableHashMap<BufferedImage> stripeMap = new
            LazyResettableHashMap<>("SubstanceProgressBarUI.stripeMap");

    /**
     * Hash for computed background images.
     */
    private static LazyResettableHashMap<BufferedImage> backgroundMap = new
            LazyResettableHashMap<>("SubstanceProgressBarUI.backgroundMap");

    /**
     * Hash for computed progress images.
     */
    private static LazyResettableHashMap<BufferedImage> progressMap = new
            LazyResettableHashMap<>("SubstanceProgressBarUI.progressMap");

    /**
     * The current position of the indeterminate animation's cycle. 0, the initial value, means
     * paint the first frame. When the progress bar is indeterminate and showing, the
     * {@link #indeterminateLoopTimeline} is updating this value.
     */
    private float animationPosition;

    /**
     * Value change listener on the associated progress bar.
     */
    private ChangeListener substanceValueChangeListener;

    /**
     * Property change listener. Tracks changes to the <code>font</code> property.
     */
    private PropertyChangeListener substancePropertyChangeListener;

    /**
     * Inner margin.
     */
    private int margin;

    private int displayedValue;

    private Timeline displayTimeline;

    private Timeline indeterminateLoopTimeline;

    public static ComponentUI createUI(JComponent comp) {
        SubstanceCoreUtilities.testComponentCreationThreadingViolation(comp);
        return new SubstanceProgressBarUI();
    }

    private SubstanceProgressBarUI() {
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();

        this.displayedValue = progressBar.getValue();
        LookAndFeel.installProperty(progressBar, "opaque", Boolean.FALSE);

        this.margin = 0;
    }

    @Override
    protected void installListeners() {
        super.installListeners();

        substanceValueChangeListener = new SubstanceChangeListener();
        this.progressBar.addChangeListener(this.substanceValueChangeListener);

        this.substancePropertyChangeListener = propertyChangeEvent -> {
            if ("font".equals(propertyChangeEvent.getPropertyName())) {
                SwingUtilities.invokeLater(() -> {
                    if (progressBar != null) {
                        progressBar.updateUI();
                    }
                });
            }
        };
        this.progressBar.addPropertyChangeListener(this.substancePropertyChangeListener);
    }

    @Override
    protected void uninstallListeners() {
        this.progressBar.removeChangeListener(this.substanceValueChangeListener);
        this.substanceValueChangeListener = null;

        this.progressBar.removePropertyChangeListener(this.substancePropertyChangeListener);
        this.substancePropertyChangeListener = null;

        super.uninstallListeners();
    }

    /**
     * Retrieves stripe image.
     *
     * @param baseSize    Stripe base in pixels.
     * @param isRotated   if <code>true</code>, the resulting stripe image will be rotated.
     * @param colorScheme Color scheme to paint the stripe image.
     * @return Stripe image.
     */
    private static BufferedImage getStripe(int baseSize, boolean isRotated,
            SubstanceColorScheme colorScheme) {
        HashMapKey key = SubstanceCoreUtilities.getHashKey(baseSize, isRotated,
                colorScheme.getDisplayName());
        BufferedImage result = SubstanceProgressBarUI.stripeMap.get(key);
        if (result == null) {
            result = SubstanceImageCreator.getStripe(baseSize, colorScheme.getUltraLightColor());
            if (isRotated) {
                result = SubstanceImageCreator.getRotated(result, 1);
            }
            SubstanceProgressBarUI.stripeMap.put(key, result);
        }
        return result;
    }

    /**
     * Returns the background of a determinate progress bar.
     *
     * @param bar                  Progress bar.
     * @param width                Progress bar width.
     * @param height               Progress bar height.
     * @param scheme               Color scheme for the background.
     * @param fillPainter          Fill painter.
     * @param orientation          Progress bar orientation (vertical / horizontal).
     * @param componentOrientation Progress bar LTR / RTL orientation.
     * @return Background image.
     */
    private static BufferedImage getDeterminateBackground(JProgressBar bar, int width, int height,
            SubstanceColorScheme scheme, SubstanceFillPainter fillPainter, int orientation,
            ComponentOrientation componentOrientation) {
        HashMapKey key = SubstanceCoreUtilities.getHashKey(width, height, scheme.getDisplayName(),
                fillPainter.getDisplayName(), orientation, componentOrientation);
        BufferedImage result = SubstanceProgressBarUI.backgroundMap.get(key);
        if (result == null) {
            result = SubstanceCoreUtilities.getBlankImage(width, height);
            Graphics2D g2d = result.createGraphics();
            float radius = 0.5f * SubstanceSizeUtils
                    .getClassicButtonCornerRadius(SubstanceSizeUtils.getComponentFontSize(bar));
            Shape contour = SubstanceOutlineUtilities.getBaseOutline(width, height, radius, null);
            fillPainter.paintContourBackground(g2d, bar, width, height, contour, false, scheme,
                    true);
            g2d.dispose();

            if (orientation == SwingConstants.VERTICAL) {
                result = SubstanceImageCreator.getRotated(result, 3);
            }
            SubstanceProgressBarUI.backgroundMap.put(key, result);
        }
        return result;
    }

    /**
     * Returns the background of a determinate progress bar.
     *
     * @param bar                  Progress bar.
     * @param width                Progress bar width.
     * @param height               Progress bar height.
     * @param scheme               Color scheme for the background.
     * @param fillPainter          Fill painter.
     * @param orientation          Progress bar orientation (vertical / horizontal).
     * @param componentOrientation Progress bar LTR / RTL orientation.
     * @return Background image.
     */
    private static BufferedImage getDeterminateProgress(JProgressBar bar, int width, int height,
            boolean isFull, SubstanceColorScheme scheme, SubstanceFillPainter fillPainter,
            int orientation, ComponentOrientation componentOrientation) {
        HashMapKey key = SubstanceCoreUtilities.getHashKey(width, height, scheme.getDisplayName(),
                fillPainter.getDisplayName(), orientation, componentOrientation);
        BufferedImage result = SubstanceProgressBarUI.progressMap.get(key);
        if (result == null) {
            result = SubstanceCoreUtilities.getBlankImage(width, height);
            Graphics2D g2d = result.createGraphics();
            float radius = 0.5f * SubstanceSizeUtils
                    .getClassicButtonCornerRadius(SubstanceSizeUtils.getComponentFontSize(bar));
            Side straightSide = (orientation == SwingConstants.VERTICAL) ? Side.RIGHT
                    :
                    (componentOrientation.isLeftToRight() ? Side.RIGHT : Side.LEFT);
            Set<Side> straightSides = isFull ? null : EnumSet.of(straightSide);
            Shape contour = SubstanceOutlineUtilities.getBaseOutline(width, height, radius,
                    straightSides);
            fillPainter.paintContourBackground(g2d, bar, width, height, contour, false, scheme,
                    true);
            g2d.dispose();

            if (orientation == SwingConstants.VERTICAL) {
                result = SubstanceImageCreator.getRotated(result, 3);
            }
            SubstanceProgressBarUI.progressMap.put(key, result);
        }
        return result;
    }

    @Override
    public void paintDeterminate(Graphics g, JComponent c) {
        if (!(g instanceof Graphics2D)) {
            return;
        }

        ComponentState fillState = getFillState();
        ComponentState progressState = getProgressState();

        int barRectWidth = progressBar.getWidth() - 2 * margin;
        int barRectHeight = progressBar.getHeight() - 2 * margin;

        // amount of progress to draw
        int amountFull = getAmountFull(new Insets(margin, margin, margin, margin), barRectWidth,
                barRectHeight);

        Graphics2D g2d = (Graphics2D) g.create();
        // install state-aware alpha channel (support for skins
        // that use translucency on disabled states).
        float stateAlpha = SubstanceColorSchemeUtilities.getAlpha(progressBar, fillState);
        g2d.setComposite(WidgetUtilities.getAlphaComposite(progressBar, stateAlpha, g));

        SubstanceColorScheme fillScheme = SubstanceColorSchemeUtilities.getColorScheme(progressBar,
                fillState);

        SubstanceFillPainter fillPainter = SubstanceCoreUtilities.getFillPainter(progressBar);
        if (progressBar.getOrientation() == SwingConstants.HORIZONTAL) {
            BufferedImage back = getDeterminateBackground(progressBar, barRectWidth, barRectHeight,
                    fillScheme, fillPainter, progressBar.getOrientation(),
                    this.progressBar.getComponentOrientation());
            NeonCortex.drawImage(g2d, back, margin, margin);
        } else {
            BufferedImage back = getDeterminateBackground(progressBar, barRectHeight, barRectWidth,
                    fillScheme, fillPainter, progressBar.getOrientation(),
                    this.progressBar.getComponentOrientation());
            NeonCortex.drawImage(g2d, back, margin, margin);
        }

        if (amountFull > 0) {
            boolean isFull = (this.progressBar.getModel().getValue() == this.progressBar
                    .getMaximum());
            SubstanceColorScheme progressColorScheme = SubstanceColorSchemeUtilities
                    .getColorScheme(progressBar, progressState);
            if (progressBar.getOrientation() == SwingConstants.HORIZONTAL) {
                int progressWidth = amountFull;
                int progressHeight = barRectHeight;
                if ((progressWidth > 0) && (progressHeight > 0)) {
                    BufferedImage progress = getDeterminateProgress(progressBar, progressWidth,
                            progressHeight, isFull, progressColorScheme, progressFillPainter,
                            progressBar.getOrientation(),
                            this.progressBar.getComponentOrientation());
                    if (progressBar.getComponentOrientation().isLeftToRight()) {
                        NeonCortex.drawImage(g2d, progress, margin, margin);
                    } else {
                        // fix for RTL determinate horizontal progress
                        // bar in 2.3
                        NeonCortex.drawImage(g2d, progress, margin + barRectWidth - amountFull,
                                margin);
                    }
                }
            } else { // VERTICAL
                int progressWidth = barRectWidth;
                int progressHeight = amountFull;
                if ((amountFull > 0) && (progressHeight > 0)) {
                    // fix for issue 95. Vertical bar is growing from
                    // the bottom
                    BufferedImage progress = getDeterminateProgress(progressBar, progressHeight,
                            progressWidth, isFull, progressColorScheme, progressFillPainter,
                            progressBar.getOrientation(),
                            this.progressBar.getComponentOrientation());
                    NeonCortex.drawImage(g2d, progress, margin,
                            margin + barRectHeight - progressHeight);
                }
            }
        }

        // Deal with possible text painting
        if (progressBar.isStringPainted()) {
            g2d.setComposite(WidgetUtilities.getAlphaComposite(progressBar, 1.0f, g));
            this.paintString(g2d, margin, margin, barRectWidth, barRectHeight, amountFull,
                    new Insets(margin, margin, margin, margin));
        }
        g2d.dispose();
    }

    @Override
    protected Color getSelectionBackground() {
        ComponentState fillState = getFillState();

        SubstanceColorScheme scheme = SubstanceColorSchemeUtilities.getColorScheme(progressBar,
                fillState);
        return SubstanceColorUtilities.getForegroundColor(scheme);
    }

    @Override
    protected Color getSelectionForeground() {
        ComponentState progressState = getProgressState();

        SubstanceColorScheme scheme = SubstanceColorSchemeUtilities.getColorScheme(progressBar,
                progressState);
        return SubstanceColorUtilities.getForegroundColor(scheme);
    }

    @Override
    public void paintIndeterminate(Graphics g, JComponent c) {
        if (!(g instanceof Graphics2D)) {
            return;
        }

        ComponentState progressState = getProgressState();

        final int barRectWidth = progressBar.getWidth() - 2 * margin;
        final int barRectHeight = progressBar.getHeight() - 2 * margin;

        int valComplete;
        if (progressBar.getOrientation() == SwingConstants.HORIZONTAL) {
            valComplete = (int) (this.animationPosition * (2 * barRectHeight + 1));
        } else {
            valComplete = (int) (this.animationPosition * (2 * barRectWidth + 1));
        }

        Graphics2D g2d = (Graphics2D) g.create();
        // install state-aware alpha channel (support for skins
        // that use translucency on disabled states).
        float stateAlpha = SubstanceColorSchemeUtilities.getAlpha(progressBar, progressState);
        g2d.setComposite(WidgetUtilities.getAlphaComposite(progressBar, stateAlpha, g));
        float radius = 0.5f * SubstanceSizeUtils
                .getClassicButtonCornerRadius(SubstanceSizeUtils.getComponentFontSize(progressBar));
        g2d.clip(new RoundRectangle2D.Float(margin, margin, barRectWidth, barRectHeight, radius,
                radius));

        SubstanceColorScheme scheme = SubstanceColorSchemeUtilities.getColorScheme(progressBar,
                progressState);
        if (progressBar.getOrientation() == SwingConstants.HORIZONTAL) {
            SubstanceImageCreator.paintRectangularStripedBackground(progressBar, g2d, margin,
                    margin, barRectWidth, barRectHeight, scheme,
                    SubstanceProgressBarUI.getStripe(barRectHeight, false, scheme), valComplete,
                    0.6f, false);
        } else {
            // fix for issue 95. Vertical progress bar grows from the
            // bottom.
            SubstanceImageCreator.paintRectangularStripedBackground(progressBar, g2d, margin,
                    margin, barRectWidth, barRectHeight, scheme,
                    SubstanceProgressBarUI.getStripe(barRectWidth, true, scheme),
                    2 * barRectWidth - valComplete, 0.6f, true);
        }

        // Deal with possible text painting
        if (progressBar.isStringPainted()) {
            g2d.setComposite(WidgetUtilities.getAlphaComposite(progressBar, 1.0f, g));
            this.paintString(g2d, margin, margin, barRectWidth, barRectHeight, barRectWidth,
                    new Insets(margin, margin, margin, margin));
        }
        g2d.dispose();
    }

    private ComponentState getFillState() {
        return progressBar.isEnabled() ? ComponentState.ENABLED
                : ComponentState.DISABLED_UNSELECTED;
    }

    private ComponentState getProgressState() {
        if (progressBar.isIndeterminate()) {
            return progressBar.isEnabled() ? INDETERMINATE_SELECTED
                    : INDETERMINATE_SELECTED_DISABLED;
        } else {
            return progressBar.isEnabled() ? DETERMINATE_SELECTED : DETERMINATE_SELECTED_DISABLED;
        }
    }

    @Override
    protected Rectangle getBox(Rectangle r) {
        // Insets b = this.getInsets(); // area for border
        int barRectWidth = progressBar.getWidth() - 2 * margin;
        int barRectHeight = progressBar.getHeight() - 2 * margin;
        return new Rectangle(margin, margin, barRectWidth, barRectHeight);
    }

    @Override
    protected void startAnimationTimer() {
        int cycleDuration = UIManager.getInt("ProgressBar.cycleTime");
        if (cycleDuration == 0) {
            cycleDuration = 1000;
        }
        this.indeterminateLoopTimeline =
                SwingComponentTimeline.componentBuilder(this.progressBar)
                        .setDuration(cycleDuration)
                        .addCallback(new TimelineCallback() {
                            @Override
                            public void onTimelineStateChanged(TimelineState oldState,
                                    TimelineState newState,
                                    float durationFraction, float timelinePosition) {
                                if ((progressBar != null) && progressBar.isVisible())
                                    progressBar.repaint();
                            }

                            @Override
                            public void onTimelinePulse(float durationFraction,
                                    float timelinePosition) {
                                if ((progressBar != null) && progressBar.isVisible())
                                    progressBar.repaint();
                            }
                        })
                        .addPropertyToInterpolate(
                                Timeline.<Float>property("animationPosition")
                                        .from(0.0f)
                                        .to(1.0f)
                                        .setWith((obj, fieldName, value) -> animationPosition = value))
                        .build();

        this.indeterminateLoopTimeline.playLoop(RepeatBehavior.LOOP);
    }

    @Override
    protected void stopAnimationTimer() {
        this.indeterminateLoopTimeline.abort();
    }

    /**
     * Returns the memory usage string.
     *
     * @return The memory usage string.
     */
    public static String getMemoryUsage() {
        StringBuffer sb = new StringBuffer();
        sb.append("SubstanceProgressBarUI: \n");
        sb.append("\t" + SubstanceProgressBarUI.stripeMap.size() + " stripes");
        return sb.toString();
    }

    @Override
    protected int getAmountFull(Insets b, int width, int height) {
        int amountFull = 0;
        BoundedRangeModel model = progressBar.getModel();

        long span = model.getMaximum() - model.getMinimum();
        double percentComplete = (double) (this.displayedValue - model.getMinimum())
                / (double) span;

        if ((model.getMaximum() - model.getMinimum()) != 0) {
            if (this.progressBar.getOrientation() == JProgressBar.HORIZONTAL) {
                amountFull = (int) Math.round(width * percentComplete);
            } else {
                amountFull = (int) Math.round(height * percentComplete);
            }
        }
        return amountFull;
    }

    @Override
    protected Dimension getPreferredInnerHorizontal() {
        int size = SubstanceSizeUtils.getComponentFontSize(this.progressBar);
        size += 2 * SubstanceSizeUtils.getAdjustedSize(size, 1, 4, 1, false);
        return new Dimension(146 + SubstanceSizeUtils.getAdjustedSize(size, 0, 1, 10, false), size);
    }

    @Override
    protected Dimension getPreferredInnerVertical() {
        int size = SubstanceSizeUtils.getComponentFontSize(this.progressBar);
        size += 2 * SubstanceSizeUtils.getAdjustedSize(size, 1, 4, 1, false);
        return new Dimension(size, 146 + SubstanceSizeUtils.getAdjustedSize(size, 0, 1, 10, false));
    }

    @Override
    protected void paintString(Graphics g, int x, int y, int width, int height, int amountFull,
            Insets b) {
        if (progressBar.getOrientation() == JProgressBar.HORIZONTAL) {
            if (progressBar.getComponentOrientation().isLeftToRight()) {
                if (progressBar.isIndeterminate()) {
                    boxRect = getBox(boxRect);
                    paintString(g, x, y, width, height, boxRect.x, boxRect.width, b);
                } else {
                    paintString(g, x, y, width, height, x, amountFull, b);
                }
            } else {
                paintString(g, x, y, width, height, x + width - amountFull, amountFull, b);
            }
        } else {
            if (progressBar.isIndeterminate()) {
                boxRect = getBox(boxRect);
                paintString(g, x, y, width, height, boxRect.y, boxRect.height, b);
            } else {
                paintString(g, x, y, width, height, y + height - amountFull, amountFull, b);
            }
        }
    }

    /**
     * Paints the progress string.
     *
     * @param g          Graphics used for drawing.
     * @param x          x location of bounding box
     * @param y          y location of bounding box
     * @param width      width of bounding box
     * @param height     height of bounding box
     * @param fillStart  start location, in x or y depending on orientation, of the filled
     *                   portion of the
     *                   progress bar.
     * @param amountFull size of the fill region, either width or height depending upon orientation.
     * @param b          Insets of the progress bar.
     */
    private void paintString(Graphics g, int x, int y, int width, int height, int fillStart,
            int amountFull, Insets b) {
        String progressString = progressBar.getString();
        Rectangle renderRectangle = getStringRectangle(progressString, x, y, width, height);

        if (progressBar.getOrientation() == JProgressBar.HORIZONTAL) {
            SubstanceTextUtilities.paintText(g, renderRectangle, progressString,
                    -1, progressBar.getFont(), getSelectionBackground(),
                    new Rectangle(amountFull, y, progressBar.getWidth() - amountFull, height));
            SubstanceTextUtilities.paintText(g, renderRectangle, progressString,
                    -1, progressBar.getFont(), getSelectionForeground(),
                    new Rectangle(fillStart, y, amountFull, height));
        } else { // VERTICAL
            SubstanceTextUtilities.paintVerticalText(g, renderRectangle,
                    progressString, -1, progressBar.getFont(), getSelectionBackground(),
                    new Rectangle(x, y, width, progressBar.getHeight() - amountFull),
                    progressBar.getComponentOrientation().isLeftToRight());
            SubstanceTextUtilities.paintVerticalText(g, renderRectangle,
                    progressString, -1, progressBar.getFont(), getSelectionForeground(),
                    new Rectangle(x, fillStart, width, amountFull),
                    progressBar.getComponentOrientation().isLeftToRight());
        }
    }

    /**
     * Returns the rectangle for the progress bar string.
     *
     * @param progressString Progress bar string.
     * @param x              x location of bounding box
     * @param y              y location of bounding box
     * @param width          width of bounding box
     * @param height         height of bounding box
     * @return The rectangle for the progress bar string.
     */
    private Rectangle getStringRectangle(String progressString, int x, int y, int width,
            int height) {
        FontMetrics fontSizer = SubstanceMetricsUtilities.getFontMetrics(progressBar.getFont());

        int stringWidth = fontSizer.stringWidth(progressString);

        if (progressBar.getOrientation() == JProgressBar.HORIZONTAL) {
            return new Rectangle(x + Math.round(width / 2 - stringWidth / 2),
                    y + (height - fontSizer.getHeight()) / 2, stringWidth, fontSizer.getHeight());
        } else {
            return new Rectangle(x + (width - fontSizer.getHeight()) / 2,
                    y + Math.round(height / 2 - stringWidth / 2), fontSizer.getHeight(),
                    stringWidth);
        }
    }

    @Override
    public void update(Graphics g, JComponent c) {
        Graphics2D g2d = (Graphics2D) g.create();
        NeonCortex.installDesktopHints(g2d, c.getFont());
        super.update(g2d, c);
        g2d.dispose();
    }
}
