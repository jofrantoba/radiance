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
package org.pushingpixels.demo.substance.main.check;

import org.pushingpixels.neon.api.font.FontSet;
import org.pushingpixels.substance.api.SubstanceCortex;
import org.pushingpixels.substance.api.SubstanceLookAndFeel;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;

/**
 * Listener to change the current locale.
 * 
 * @author Kirill Grouchnikov
 */
public class MyLocaleChangeListener implements ActionListener {
    /**
     * Language code.
     */
    private String langCode;

    /**
     * Country code.
     */
    private String countryCode;

    /**
     * Main test frame.
     */
    private JFrame frame;

    /**
     * Wrapper around the base Substance font set. Is used to create larger / smaller font sets.
     * 
     * @author Kirill Grouchnikov
     */
    private static class DialogFontSet implements FontSet {
        /**
         * The base Substance font set.
         */
        private FontSet delegate;

        /**
         * Creates a wrapper font set.
         * 
         * @param delegate
         *            The base Substance font set.
         */
        public DialogFontSet(FontSet delegate) {
            super();
            this.delegate = delegate;
        }

        /**
         * Returns the wrapped font.
         * 
         * @param systemFont
         *            Original font.
         * @return Wrapped font.
         */
        private FontUIResource getWrappedFont(FontUIResource systemFont) {
            return new FontUIResource("Dialog", systemFont.getStyle(), systemFont.getSize());
        }

        public FontUIResource getControlFont() {
            return this.getWrappedFont(this.delegate.getControlFont());
        }

        public FontUIResource getMenuFont() {
            return this.getWrappedFont(this.delegate.getMenuFont());
        }

        public FontUIResource getMessageFont() {
            return this.getWrappedFont(this.delegate.getMessageFont());
        }

        public FontUIResource getSmallFont() {
            return this.getWrappedFont(this.delegate.getSmallFont());
        }

        public FontUIResource getTitleFont() {
            return this.getWrappedFont(this.delegate.getTitleFont());
        }

        public FontUIResource getWindowTitleFont() {
            return this.getWrappedFont(this.delegate.getWindowTitleFont());
        }
    }

    /**
     * Creates the locale change listener.
     * 
     * @param langCode
     *            Language code.
     * @param countryCode
     *            Country code.
     * @param frame
     *            Main test frame.
     */
    public MyLocaleChangeListener(String langCode, String countryCode, JFrame frame) {
        this.langCode = langCode;
        this.countryCode = countryCode;
        this.frame = frame;
    }

    public void actionPerformed(ActionEvent e) {
        SwingUtilities.invokeLater(() -> changeLocale(frame, countryCode, langCode));
    }

    public static void changeLocale(JFrame frame, String countryCode, String langCode) {
        LookAndFeel currLaf = UIManager.getLookAndFeel();
        Locale newLocale = new Locale(langCode, countryCode);
        Locale.setDefault(newLocale);
        frame.applyComponentOrientation(
                ComponentOrientation.getOrientation(Locale.getDefault()));
        if (currLaf instanceof SubstanceLookAndFeel) {
            SubstanceCortex.GlobalScope.resetLabelBundle();
            if ("CN".equals(countryCode)) {
                final FontSet currFontSet = SubstanceCortex.GlobalScope.getFontPolicy()
                        .getFontSet();
                SubstanceCortex.GlobalScope.setFontPolicy(
                        () -> new DialogFontSet(currFontSet));
            } else {
                SubstanceCortex.GlobalScope.setFontPolicy(null);
            }
        }
        try {
            UIManager.setLookAndFeel(currLaf.getClass().getName());
        } catch (Exception exc) {
        }
        SwingUtilities.updateComponentTreeUI(frame);
    }

}