/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.refimpl.bridge;

import java.awt.Color;
import java.awt.Composite;
import java.awt.GraphicsEnvironment;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.net.MalformedURLException;
import java.net.URL;

import java.io.StringReader;

import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.BridgeMutationEvent;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.GraphicsNodeBridge;
import org.apache.batik.bridge.IllegalAttributeValueException;
import org.apache.batik.dom.svg.SVGOMDocument;
import org.apache.batik.dom.util.XLinkSupport;
import org.apache.batik.dom.util.XMLSupport;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.gvt.GraphicsNodeRenderContext;
import org.apache.batik.gvt.TextNode;
import org.apache.batik.gvt.filter.Clip;
import org.apache.batik.gvt.filter.Filter;
import org.apache.batik.gvt.filter.Mask;
import org.apache.batik.gvt.text.GVTAttributedCharacterIterator;
import org.apache.batik.refimpl.bridge.resources.Messages;
import org.apache.batik.util.SVGConstants;
import org.apache.batik.util.UnitProcessor;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.css.CSSPrimitiveValue;
import org.w3c.dom.css.CSSStyleDeclaration;
import org.w3c.dom.css.CSSValue;
import org.w3c.dom.css.CSSValueList;
import org.w3c.dom.svg.SVGElement;
import org.w3c.dom.svg.SVGDocument;

/**
 * A factory for the &lt;text&gt; SVG element.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 * @version $Id$
 */
public class SVGTextElementBridge implements GraphicsNodeBridge, SVGConstants {
    protected final static Map fonts = new HashMap(11);
    static {
        fonts.put("serif",           "Serif");
        fonts.put("Times",           "Serif");
        fonts.put("Times New Roman", "Serif");
        fonts.put("sans-serif",      "SansSerif");
        fonts.put("cursive",         "Dialog");
        fonts.put("fantasy",         "Symbol");
        fonts.put("monospace",       "Monospaced");
        fonts.put("monospaced",      "Monospaced");
        fonts.put("Courier",         "Monospaced");

        //
        // Load all fonts. Work around
        //
        /* Note to maintainer:  font init code should not be here!
         * we should not have dependencies in the Bridge
         * on java2d Fonts - they are not relevant to non-rasterizing
         * renderers!
         * We should instead support a list of fonts, in order of preference,
         * as CSS allows, and defer resolving these names to actual
         * implementation-dependent font names until render time.
         *
         *                -Bill Haneman
         */
        GraphicsEnvironment env;
        env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        // System.out.println("Initializing fonts .... please wait");
        String fontNames[] = env.getAvailableFontFamilyNames();
        int nFonts = fontNames != null ? fontNames.length : 0;
        // System.out.println("Done initializing " + nFonts + " fonts");
        for(int i=0; i<nFonts; i++){
            fonts.put(fontNames[i], fontNames[i]);
            //System.out.println(fontNames[i]);
        }
    }

    public GraphicsNode createGraphicsNode(BridgeContext ctx,
                                           Element element){
        TextNode result = ctx.getGVTFactory().createTextNode();
        result.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);


        CSSStyleDeclaration cssDecl
            = ctx.getViewCSS().getComputedStyle(element, null);
        UnitProcessor.Context uctx
            = new DefaultUnitProcessorContext(ctx, cssDecl);

        // Transform
        AffineTransform at =
            SVGUtilities.convertAffineTransform(element,
                                                ATTR_TRANSFORM,
                                                ctx.getParserFactory());
        result.setTransform(at);

        // parse the x attribute, (default is 0)
        String s = element.getAttributeNS(null, SVG_X_ATTRIBUTE);
        float x = 0;
        if (s.length() != 0) {
            x = SVGUtilities.svgToUserSpace(element,
                                            SVG_X_ATTRIBUTE, s,
                                            uctx,
                                            UnitProcessor.HORIZONTAL_LENGTH);
        }

        // parse the y attribute, (default is 0)
        s = element.getAttributeNS(null, SVG_Y_ATTRIBUTE);
        float y = 0;
        if (s.length() != 0) {
            y = SVGUtilities.svgToUserSpace(element,
                                            SVG_Y_ATTRIBUTE, s,
                                            uctx,
                                            UnitProcessor.VERTICAL_LENGTH);
        }

        result.setLocation(new Point2D.Float(x, y));

        return result;
    }

    public void buildGraphicsNode(GraphicsNode node,
                                  BridgeContext ctx,
                                  Element element) {

        TextNode result = (TextNode)node;

        CSSStyleDeclaration cssDecl
            = ctx.getViewCSS().getComputedStyle(element, null);
        UnitProcessor.Context uctx
            = new DefaultUnitProcessorContext(ctx, cssDecl);

        // Text-anchor
        CSSPrimitiveValue v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_TEXT_ANCHOR_PROPERTY);
        String s = v.getStringValue();
        TextNode.Anchor a;
        switch (s.charAt(0)) {
        case 's':
            a = TextNode.Anchor.START;
            break;
        case 'm':
            a = TextNode.Anchor.MIDDLE;
            break;
        default:
            a = TextNode.Anchor.END;
        }
        result.setAnchor(a);

        element.normalize();

        AttributedString as = buildAttributedString(ctx,
                                                    element,
                                                    result);
        result.setAttributedCharacterIterator(as.getIterator());

        // Set the node filter
        Filter filter = CSSUtilities.convertFilter(element, result, ctx);
        result.setFilter(filter);

        // Set the node mask
        Mask mask = CSSUtilities.convertMask(element, result, ctx);
        result.setMask(mask);

        // Set the node clip
        Clip clip = CSSUtilities.convertClipPath(element, result, ctx);
        result.setClip(clip);

        // <!> TODO only when binding is enabled
        BridgeEventSupport.addDOMListener(ctx, element);
        ctx.bind(element, result);
    }

    public void update(BridgeMutationEvent evt) {
        // <!> FIXME : TODO
    }

    public boolean isContainer() {
        return false;
    }

    /**
     * Creates the attributed string which represent the given text
     * element children.
     */
    protected AttributedString buildAttributedString(BridgeContext ctx,
                                             Element element,
                                             GraphicsNode node) {


        AttributedString result = null;

        List l = buildAttributedStrings(ctx,
                                        element,
                                        node,
                                        true,
                                        new LinkedList());
        // Simple cases
        switch (l.size()) {
        case 0:
            return new AttributedString(" ");
        case 1:
            return (AttributedString)l.get(0);
        }

        //
        // Merge the attributed strings
        //
        List buffers = new LinkedList();
        List maps = new LinkedList();
        Iterator it = l.iterator();

        // First pass: build the string buffer.
        StringBuffer sb = new StringBuffer();
        while (it.hasNext()) {
            AttributedString s = (AttributedString)it.next();
            AttributedCharacterIterator aci = s.getIterator();
            // Build the StringBuffer
            char c = aci.first();
            for (; c != CharacterIterator.DONE; c = aci.next()) {
                sb.append(c);
            }
        }

        result = new AttributedString(sb.toString());

        // Second pass: decorate the attributed string.
        int i=0;
        it = l.iterator();
        while (it.hasNext()) {
            AttributedString s = (AttributedString)it.next();
            AttributedCharacterIterator aci = s.getIterator();
            Iterator attrIter = aci.getAllAttributeKeys().iterator();
            while (attrIter.hasNext()) { // for each attribute key...
                AttributedCharacterIterator.Attribute key =
                    (AttributedCharacterIterator.Attribute) attrIter.next();

                int begin;
                int end;
                aci.first();
                do {
                    begin = aci.getRunStart(key);
                    end = aci.getRunLimit(key);
                    aci.setIndex(begin);
                    Object value = aci.getAttribute(key);
                    //System.out.println("Adding attribute "+key+": "+value+" from "+(i+begin)+"->"+(i+end));
                    result.addAttribute(key, value, i+begin, i+end);
                    aci.setIndex(end);
                } while (end < aci.getEndIndex()); // more runs in aci
            }
            i += aci.getEndIndex();
        }

        AttributedCharacterIterator iter = result.getIterator();
        int ch = iter.first();
        while (ch != CharacterIterator.DONE) {
            Float dx = (Float) iter.getAttribute(
                           GVTAttributedCharacterIterator.TextAttribute.DX);
            Float dy = (Float) iter.getAttribute(
                           GVTAttributedCharacterIterator.TextAttribute.DY);
            //System.out.println((char) ch+" deltas : ("+dx+","+dy+")");
            Float x = (Float) iter.getAttribute(
                           GVTAttributedCharacterIterator.TextAttribute.X);
            Float y = (Float) iter.getAttribute(
                           GVTAttributedCharacterIterator.TextAttribute.Y);
            //System.out.println((char) ch+" pos : ("+x+","+y+")");
            ch = iter.next();
        }
        return result;
    }

    /**
     * Creates the attributed strings which represent the given text
     * element children.
     */
    protected List buildAttributedStrings(BridgeContext ctx,
                                          Element element,
                                          GraphicsNode node,
                                          boolean top,
                                          LinkedList result) {

        // !!! return two lists

        Map m = getAttributeMap(ctx, element, node);
        String s = XMLSupport.getXMLSpace(element);
        boolean preserve = s.equals("preserve");
        boolean first = true;
        boolean last;
        boolean stripFirst = true;
        boolean stripLast = true;
        Element nodeElement = element;
        AttributedString as = null;

        if (!result.isEmpty()) {
            as = (AttributedString) result.getLast();
        }

        for (Node n = element.getFirstChild();
             n != null;
             n = n.getNextSibling()) {

            last = n.getNextSibling() == null;

            int lastChar = (as != null) ?
                  (as.getIterator().last()) : CharacterIterator.DONE;
            stripFirst = first &&
                  (top ||
                  (lastChar == ' ') ||
                  (lastChar == CharacterIterator.DONE));

            switch (n.getNodeType()) {
            case Node.ELEMENT_NODE:

                nodeElement = (Element)n;

                if (n.getLocalName().equals("tspan")) {
                    buildAttributedStrings(ctx,
                                           nodeElement,
                                           node,
                                           false,
                                           result);
                } else if (n.getLocalName().equals("tref")) {
                    try {
                        String uriStr = XLinkSupport.getXLinkHref((Element)n);
                        SVGDocument svgDoc =
                            (SVGDocument)element.getOwnerDocument();
                        URL baseURL = ((SVGOMDocument)svgDoc).getURLObject();
                        URL url = new URL(baseURL, uriStr);

                        DocumentLoader loader = ctx.getDocumentLoader();
                        URIResolver resolver = new URIResolver(svgDoc, loader);
                        Element ref = (Element)resolver.getNode(url.toString());
                        s = getElementContent(ref);
                        Map map = getAttributeMap(ctx, nodeElement, node);
                        int[] indexMap = new int[s.length()];
                        as = createAttributedString(s, map, indexMap, preserve,
                                                    stripFirst, last && top);
                        if (as != null) {
                            addGlyphPositionAttributes(
                                 as, indexMap, ctx, nodeElement);
                            stripLast = (as.getIterator().first() == ' ');
                            if (stripLast) {
                                AttributedString las =
                                     (AttributedString) result.removeLast();
                                if (las != null) {
                                    AttributedCharacterIterator iter = las.getIterator();
                                    int endIndex = iter.getEndIndex()-1;
                                    if (iter.setIndex(endIndex) == ' ') {
                                         las = new AttributedString(
                                             las.getIterator(null,
                                                 iter.getBeginIndex(), endIndex));
                                    }
                                    result.add(las);
                                }
                            }
                            result.add(as);
                        }
                    } catch(MalformedURLException ex) {
                        throw new IllegalAttributeValueException(
                         Messages.formatMessage("tref.xlinkHref.badURL", null));
                    } catch (Exception ex) { /* Nothing to do */ }
                }
                break;
            case Node.TEXT_NODE:
                s = n.getNodeValue();
                int[] indexMap = new int[s.length()];
                as = createAttributedString(
                              s, m, indexMap, preserve, stripFirst, last && top);
                if (as != null) {
                     addGlyphPositionAttributes(as, indexMap, ctx, element);
                     stripLast = (as.getIterator().first() == ' ');
                     if (stripLast && !result.isEmpty()) {
                         AttributedString las =
                              (AttributedString) result.removeLast();
                         if (las != null) {
                             AttributedCharacterIterator iter = las.getIterator();
                             int endIndex = iter.getEndIndex()-1;
                             if (iter.setIndex(endIndex) == ' ') {
                                 las = new AttributedString(
                                         las.getIterator(null,
                                             iter.getBeginIndex(), endIndex));
                             }
                             result.add(las);
                         }
                     }
                     result.add(as);
                }
            }
            first = false;
        }
        return result;
    }

    /**
     * Returns the content of the given element.
     */
    protected String getElementContent(Element e) {
        StringBuffer result = new StringBuffer();
        for (Node n = e.getFirstChild();
             n != null;
             n = n.getNextSibling()) {
            switch (n.getNodeType()) {
            case Node.ELEMENT_NODE:
                result.append(getElementContent((Element)n));
                break;
            case Node.TEXT_NODE:
                result.append(n.getNodeValue());
            }
        }
        return result.toString();
    }

    /**
     * Creates an attributes string from the content of the given string.
     */
    protected AttributedString createAttributedString(String s,
                                                      Map m,
                                                      int[] indexMap,
                                                      boolean preserve,
                                                      boolean stripfirst,
                                                      boolean striplast) {
        AttributedString as = null;
        StringBuffer sb = new StringBuffer();
        if (preserve) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                case 10:
                case 13:
                case '\t':
                    sb.append(' ');
                    break;
                default:
                    sb.append(c);
                }
                indexMap[i] = i;
            }
        } else {
            boolean space = false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                case 10:
                case 13:
                    break; // should break, newlines are not whitespace
                case ' ':
                case '\t':
                    if (!space) {
                        sb.append(' ');
                        indexMap[sb.length()-1] = i;
                        space = true;
                    }
                    break;
                default:
                    sb.append(c);
                    indexMap[sb.length()-1] = i;
                    space = false;
                }
            }
            if (stripfirst) {
                while (sb.length() > 0) {
                    if (sb.charAt(0) == ' ') {
                        sb.deleteCharAt(0);
                        System.arraycopy(indexMap, 1, indexMap, 0, sb.length());
                    } else {
                        break;
                    }
                }
             }
             if (striplast) {
                int len;
                while ((len = sb.length()) > 0) {
                    if (sb.charAt(len - 1) == ' ') {
                        sb.deleteCharAt(len - 1);
                    } else {
                        break;
                    }
                }
             }
             for (int i=sb.length(); i<indexMap.length; ++i) {
                indexMap[i] = -1;
             }
        }
        if (sb.length() > 0) {
            as = new AttributedString(sb.toString(), m);
        }

        return as;
    }

    /**
     * Adds glyph position attributes to an AttributedString.
     */
    protected void addGlyphPositionAttributes(AttributedString as,
                                  int[] indexMap,
                                  BridgeContext ctx,
                                  Element element) {

        CSSStyleDeclaration cssDecl
            = ctx.getViewCSS().getComputedStyle(element, null);
        UnitProcessor.Context uctx
            = new DefaultUnitProcessorContext(ctx, cssDecl);

        int asLength =
            as.getIterator().getEndIndex();
            // AttributedStrings always start at index 0, we hope!

        // glyph and sub-element positions
        // (Don't do these for <text> elements
        if (((Node)element).getLocalName() != "text") {
            String s = element.getAttributeNS(null, SVG_X_ATTRIBUTE);
            //System.out.println("X: "+s);
            if (s.length() != 0) {
                float x[] = SVGUtilities.svgToUserSpaceArray(element,
                                            SVG_X_ATTRIBUTE, s,
                                            uctx,
                                            UnitProcessor.HORIZONTAL_LENGTH);

                for (int i=0; i<asLength; ++i) {
                    if (i < x.length) {
                            as.addAttribute(
                                GVTAttributedCharacterIterator.TextAttribute.X,
                                    new Float(x[i]), i, i+1);
                    }
                }
            }
            // parse the y attribute, (default is 0)
            s = element.getAttributeNS(null, SVG_Y_ATTRIBUTE);
            //System.out.println("Y: "+s);
            if (s.length() != 0) {
                float y[] = SVGUtilities.svgToUserSpaceArray(element,
                                            SVG_Y_ATTRIBUTE, s,
                                            uctx,
                                            UnitProcessor.VERTICAL_LENGTH);

                for (int i=0; i<asLength; ++i) {
                    if (i < y.length) {
                        as.addAttribute(
                            GVTAttributedCharacterIterator.TextAttribute.Y,
                                new Float(y[i]), i, i+1);
                    }
                }

            }
            s = element.getAttributeNS(null, SVG_DX_ATTRIBUTE);
            //System.out.println("DX: "+s);
            if (s.length() != 0) {
                float x[] = SVGUtilities.svgToUserSpaceArray(element,
                                            SVG_DX_ATTRIBUTE, s,
                                            uctx,
                                            UnitProcessor.HORIZONTAL_LENGTH);
                int i=0;
                float cx = 0f;
                for (int n=0; n<indexMap.length && indexMap[n] >= 0; ++n) {
                    boolean hasAnother = false;
                    while ((indexMap[n] != i) && (i<x.length)) {
                        cx += x[i];
                        ++i;
                        hasAnother = true;
                    }
                    if ((i<x.length) && (indexMap[n] == i)) {
                        cx += x[i];
                        hasAnother = true;
                    }
                    if (hasAnother) as.addAttribute(
                          GVTAttributedCharacterIterator.TextAttribute.DX,
                                    new Float(cx), n, n+1);
                    cx = 0f;
                }
            }
            // parse the y attribute, (default is 0)
            s = element.getAttributeNS(null, SVG_DY_ATTRIBUTE);
            //System.out.println("DY: "+s);
            if (s.length() != 0) {
                float y[] = SVGUtilities.svgToUserSpaceArray(element,
                                            SVG_DY_ATTRIBUTE, s,
                                            uctx,
                                            UnitProcessor.VERTICAL_LENGTH);
                int i=0;
                float cy = 0f;
                for (int n=0; n<indexMap.length && indexMap[n] >= 0; ++n) {
                    boolean hasAnother = false;
                    while ((indexMap[n] != i) && (i<y.length)) {
                        cy += y[i];
                        ++i;
                        hasAnother = true;
                    }
                    if ((i<y.length) && (indexMap[n] == i)) {
                        cy += y[i];
                        hasAnother = true;
                    }
                    if (hasAnother) as.addAttribute(
                           GVTAttributedCharacterIterator.TextAttribute.DY,
                                    new Float(cy), n, n+1);
                    //System.out.println("Setting DY to "+cy+" at "+n);
                    cy = 0f;
                }
            }
        }
    }

    /**
     * Returns the map to pass to the current characters.
     */
    protected Map getAttributeMap(BridgeContext ctx,
                                  Element element,
                                  GraphicsNode node) {

        CSSStyleDeclaration cssDecl
            = ctx.getViewCSS().getComputedStyle(element, null);
        UnitProcessor.Context uctx
            = new DefaultUnitProcessorContext(ctx, cssDecl);

        Map result = new HashMap();
        CSSPrimitiveValue v;

        result.put(GVTAttributedCharacterIterator.TextAttribute.TEXT_COMPOUND_DELIMITER, element);

        // Font size, in user space units.
        float fs = CSSUtilities.convertFontSize((SVGElement)element,
                                                ctx,
                                                cssDecl,
                                                uctx);

        result.put(TextAttribute.SIZE, new Float(fs));

        // Font family
        CSSValueList ff = (CSSValueList)cssDecl.getPropertyCSSValue
            (CSS_FONT_FAMILY_PROPERTY);
        String s = null;
        for (int i = 0; s == null && i < ff.getLength(); i++) {
            v = (CSSPrimitiveValue)ff.item(i);
            s = (String)fonts.get(v.getStringValue());
        }
        s = (s == null) ? "SansSerif" : s;
        result.put(TextAttribute.FAMILY, s);

        // Font weight
        // TODO: improve support for relative values
        // (e.g. "lighter", "bolder")
        v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_FONT_WEIGHT_PROPERTY);
        if (v.getPrimitiveType() == CSSPrimitiveValue.CSS_IDENT) {
            if (v.getStringValue().charAt(0) == 'n') {
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_REGULAR);
            } else if (v.getStringValue().charAt(0) == 'l') {
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_LIGHT);
            } else {
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_BOLD);
            }
        } else {
            switch ((int)v.getFloatValue(CSSPrimitiveValue.CSS_NUMBER)) {
            case 100:
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_EXTRA_LIGHT);
                break;
            case 200:
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_LIGHT);
                break;
            case 300:
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_DEMILIGHT);
                break;
            case 400:
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_REGULAR);
                break;
            case 500:
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_SEMIBOLD);
                break;
            case 600:
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_DEMIBOLD);
                break;
            case 700:
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_BOLD);
                break;
            case 800:
                result.put(TextAttribute.WEIGHT,
                           //TextAttribute.WEIGHT_EXTRABOLD);
                           TextAttribute.WEIGHT_BOLD);
                break;
            case 900:
                result.put(TextAttribute.WEIGHT,
                           //TextAttribute.WEIGHT_ULTRABOLD);
                           TextAttribute.WEIGHT_BOLD);
            }
        }

        // Text baseline adjustment.
        // TODO: support for <percentage> and <length> values.
        v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_BASELINE_SHIFT_PROPERTY);
        if (v.getPrimitiveType() == CSSPrimitiveValue.CSS_IDENT) {
            s = v.getStringValue();
            switch (s.charAt(2)) {
            case 'p': //suPerscript
                result.put(TextAttribute.SUPERSCRIPT,
                       TextAttribute.SUPERSCRIPT_SUPER);
                break;
            case 'b': //suBscript
                result.put(TextAttribute.SUPERSCRIPT,
                       TextAttribute.SUPERSCRIPT_SUB);
                break;
            case 's': //baSeline
                result.put(TextAttribute.SUPERSCRIPT,
                       new Integer(0)); // XXX: jdk1.2 doesn't define
                                        // SUPERSCRIPT_NONE, 1.3 does
                break;
            }
        } else {
            // TODO
            float f = v.getFloatValue(CSSPrimitiveValue.CSS_NUMBER);
            // result.put(TextAttribute.TRANSFORM,
            //           CSSUtilities.convertFloatToTranslation(0f, f));
        }

        // Unicode-bidi mode
        // XXX: below supports single override level only,
        // full support requires revision: see comments
        // below regarding 'direction'

        v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_UNICODE_BIDI_PROPERTY);
        s = v.getStringValue();
        switch (s.charAt(0)) {
        case 'e':
            result.put(TextAttribute.BIDI_EMBEDDING,
                       new Integer(1));
            break;
        case 'b':
            result.put(TextAttribute.BIDI_EMBEDDING,
                       new Integer(-1));
            break;
        case 'n':
            result.put(TextAttribute.BIDI_EMBEDDING,
                       new Integer(0));
        }

        // Text direction
        // XXX: this needs to coordinate with the unicode-bidi
        // property, so that when an explicit reversal
        // occurs, the BIDI_EMBEDDING level is
        // appropriately incremented or decremented.
        // Note that direction is implicitly handled by unicode
        // BiDI algorithm in most cases, this property
        // is only needed when one wants to override the
        // normal writing direction for a string/substring.

        v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_DIRECTION_PROPERTY);
        s = v.getStringValue();
        switch (s.charAt(0)) {
        case 'l':
            result.put(TextAttribute.RUN_DIRECTION,
                       TextAttribute.RUN_DIRECTION_LTR);
            break;
        case 'r':
            result.put(TextAttribute.RUN_DIRECTION,
                       TextAttribute.RUN_DIRECTION_RTL);
            break;
        }

        // Writing mode

        v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_WRITING_MODE_PROPERTY);
        s = v.getStringValue();
        switch (s.charAt(0)) {
        case 'l':
            result.put(TextAttribute.RUN_DIRECTION,
                       TextAttribute.RUN_DIRECTION_LTR);
            break;
        case 'r':
            result.put(TextAttribute.RUN_DIRECTION,
                       TextAttribute.RUN_DIRECTION_RTL);
            break;
        case 't':
            break;
        }

        // Font style
        v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_FONT_STYLE_PROPERTY);
        s = v.getStringValue();
        switch (s.charAt(0)) {
        case 'n':
            result.put(TextAttribute.POSTURE,
                       TextAttribute.POSTURE_REGULAR);
            break;
        case 'o':
        case 'i':
            result.put(TextAttribute.POSTURE,
                       TextAttribute.POSTURE_OBLIQUE);
        }

        // Font stretch
        v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_FONT_STRETCH_PROPERTY);
        s = v.getStringValue();
        switch (s.charAt(0)) {
        case 'u':
            if (s.charAt(6) == 'c') {
                result.put(TextAttribute.WIDTH,
                           TextAttribute.WIDTH_CONDENSED);
            } else {
                result.put(TextAttribute.WIDTH,
                           TextAttribute.WIDTH_EXTENDED);
            }
            break;
        case 'e':
            if (s.charAt(6) == 'c') {
                result.put(TextAttribute.WIDTH,
                           TextAttribute.WIDTH_CONDENSED);
            } else {
                if (s.length() == 8) {
                    result.put(TextAttribute.WIDTH,
                               TextAttribute.WIDTH_SEMI_EXTENDED);
                } else {
                    result.put(TextAttribute.WIDTH,
                               TextAttribute.WIDTH_EXTENDED);
                }
            }
            break;
        case 's':
            if (s.charAt(6) == 'c') {
                result.put(TextAttribute.WIDTH,
                           TextAttribute.WIDTH_SEMI_CONDENSED);
            } else {
                result.put(TextAttribute.WIDTH,
                           TextAttribute.WIDTH_SEMI_EXTENDED);
            }
            break;
        default:
            result.put(TextAttribute.WIDTH,
                       TextAttribute.WIDTH_REGULAR);
        }

        // Opacity
        CSSPrimitiveValue opacityVal =
            (CSSPrimitiveValue)cssDecl.getPropertyCSSValue(ATTR_OPACITY);
        if (opacityVal != null) {
            Composite composite =
                CSSUtilities.convertOpacityToComposite(opacityVal);
            result.put(GVTAttributedCharacterIterator.TextAttribute.OPACITY,
                                                                     composite);
        }

        // Fill
        Paint p = CSSUtilities.convertFillToPaint((SVGElement)element,
                                                  node,
                                                  ctx,
                                                  cssDecl,
                                                  uctx);

        if (p != null) {
            result.put(TextAttribute.FOREGROUND, p);
        }

        // Stroke Paint
        Paint sp = CSSUtilities.convertStrokeToPaint((SVGElement)element,
                                              node,
                                              ctx,
                                              cssDecl,
                                              uctx);

        if (sp != null) {
            result.put
                (GVTAttributedCharacterIterator.TextAttribute.STROKE_PAINT, sp);
        }

        // Stroke
        Stroke stroke
            = CSSUtilities.convertStrokeToBasicStroke((SVGElement)element,
                                                      ctx,
                                                      cssDecl,
                                                      uctx);

        if(stroke != null){
            result.put(GVTAttributedCharacterIterator.TextAttribute.STROKE,
                       stroke);
        }

        // Text decoration
        CSSValue cssVal = cssDecl.getPropertyCSSValue
            (CSS_TEXT_DECORATION_PROPERTY);
        short t = cssVal.getCssValueType();
        if (t == CSSValue.CSS_VALUE_LIST) {
            CSSValueList lst = (CSSValueList)cssVal;
            for (int i = 0; i < lst.getLength(); i++) {
                v = (CSSPrimitiveValue)lst.item(i);
                s = v.getStringValue();
                switch (s.charAt(0)) {
                case 'u':
                    result.put(
                        GVTAttributedCharacterIterator.TextAttribute.UNDERLINE,
                        GVTAttributedCharacterIterator.TextAttribute.UNDERLINE_ON);
                    if (sp != null) {
                        result.put(GVTAttributedCharacterIterator.
                                   TextAttribute.UNDERLINE_STROKE_PAINT, sp);
                    }
                    if (stroke != null) {
                        result.put(GVTAttributedCharacterIterator.
                                   TextAttribute.UNDERLINE_STROKE, stroke);
                    }
                    if (p != null) {
                        result.put(GVTAttributedCharacterIterator.
                                   TextAttribute.UNDERLINE_PAINT, p);
                    }
                    break;
                case 'o':
                    result.put(GVTAttributedCharacterIterator.TextAttribute.OVERLINE,
                               GVTAttributedCharacterIterator.TextAttribute.OVERLINE_ON);
                    break;
                case 'l':
                    result.put(
                        GVTAttributedCharacterIterator.TextAttribute.STRIKETHROUGH,
                        GVTAttributedCharacterIterator.TextAttribute.STRIKETHROUGH_ON);
                }
            }
        }

        return result;
    }

}
