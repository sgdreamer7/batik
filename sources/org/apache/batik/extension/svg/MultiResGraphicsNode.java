/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.extension.svg;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.lang.ref.SoftReference;

import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.CSSUtilities;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.SVGBrokenLinkProvider;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.bridge.ViewBox;
import org.apache.batik.ext.awt.image.renderable.ClipRable8Bit;
import org.apache.batik.ext.awt.image.renderable.Filter;
import org.apache.batik.ext.awt.image.spi.ImageTagRegistry;
import org.apache.batik.gvt.AbstractGraphicsNode;
import org.apache.batik.gvt.CompositeGraphicsNode;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.gvt.RasterImageNode;
import org.apache.batik.util.ParsedURL;
import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;
import org.w3c.dom.svg.SVGDocument;
import org.w3c.dom.svg.SVGSVGElement;

/**
 * RasterRable This is used to wrap a Rendered Image back into the
 * RenderableImage world.
 *
 * @author <a href="mailto:Thomas.DeWeese@Kodak.com>Thomas DeWeese</a>
 * @version $Id$
 */
public class MultiResGraphicsNode
    extends AbstractGraphicsNode implements SVGConstants {

    SoftReference [] srcs;
    ParsedURL     [] srcURLs;
    Dimension     [] minSz;
    Dimension     [] maxSz;
    Rectangle2D      bounds;

    UserAgent      userAgent;
    DocumentLoader loader;
    BridgeContext  ctx;

    Element multiImgElem;

    /**
     * The error code when a required attribute is missing.
     * {0} = the name of the attribute
     */
    public static final String ERR_ATTRIBUTE_MISSING
        = "attribute.missing";


    public MultiResGraphicsNode(Element multiImgElem,
                                Rectangle2D  bounds,
                                ParsedURL [] srcURLs,
                                Dimension [] minSz,
                                Dimension [] maxSz) {
        this.multiImgElem = multiImgElem;
        this.srcURLs     = new ParsedURL[srcURLs.length];
        this.minSz       = new Dimension[srcURLs.length];
        this.maxSz       = new Dimension[srcURLs.length];

        for (int i=0; i<srcURLs.length; i++) {
            this.srcURLs[i] = srcURLs[i];
            this.minSz[i]   = minSz[i];
            this.maxSz[i]   = maxSz[i];
        }

        this.srcs = new SoftReference[srcURLs.length];
        this.bounds = bounds;
        userAgent = new UserAgentAdapter();
        loader    = new DocumentLoader(userAgent);
        ctx       = new BridgeContext(userAgent, loader);
    }

    /**
     * Paints this node without applying Filter, Mask, Composite, and clip.
     *
     * @param g2d the Graphics2D to use
     */
    public void primitivePaint(Graphics2D g2d) {
        // System.err.println("PrimPaint: " + this);
        // get the current affine transform
        AffineTransform at = g2d.getTransform();

        double scx = Math.sqrt(at.getShearX()*at.getShearX()+
                               at.getScaleX()*at.getScaleX());

        GraphicsNode gn = null;
        int idx =-1;
        double w = bounds.getWidth()*scx;
        double minDist = calcDist(w, minSz[0], maxSz[0]);
        int    minIdx = 0;
        // System.err.println("Width: " + w);
        for (int i=0; i<minSz.length; i++) {
            double dist = calcDist(w, minSz[i], maxSz[i]);
            // System.err.println("Dist: " + dist);
            if (dist < minDist) {
                minDist = dist;
                minIdx = i;
            } 
                
            if (((minSz[i] == null) || (w >= minSz[i].width)) &&
                ((maxSz[i] == null) || (w <= maxSz[i].width))) {
                // We have a range match
                // System.err.println("Match: " + i + " " + 
                //                    minSz[i] + " -> " + maxSz[i]);
                if ((idx == -1) || (minIdx == i)) {
                    idx = i;
                }
            }
        }

        if (idx == -1)
            idx = minIdx;
        gn = getGraphicsNode(idx);

        if (gn == null) return;

        // Rectangle2D gnBounds = gn.getBounds();
        // double sx = bounds.getWidth()/sizes[idx].getWidth();
        // double sy = bounds.getHeight()/sizes[idx].getHeight();
        // System.err.println("Scale: [" + sx + ", " + sy + "]");

        gn.paint(g2d);
    }

    // This function can be tweaked to any extent.  This is a very
    // simple measure of 'goodness'.  It has two main flaws as is,
    // mostly in regards to distance calc with 'unbounded' ranges.
    // First it doesn't punish if the distance is the wrong way on the
    // unbounded range (so over a max by 10 is the same as under a max
    // by 10) this is compensated by the absolute preference for
    // matches 'in range' above.  The other issue is that unbounded
    // ranages tend to 'win' when the value is near the boundry point
    // since they use distance from the boundry point rather than the
    // middle of the range.  As it is this seems to meet all the
    // requirements of the SVG specification however.
    public double calcDist(double loc, Dimension min, Dimension max) {
        if (min == null) {
            if (max == null) 
                return 10E10; // very large number.
            else
                return Math.abs(loc-max.width);
        } else {
            if (max == null) 
                return Math.abs(loc-min.width);
            else {
                double mid = (max.width+min.width)/2.0;
                return Math.abs(loc-mid);
            }
        }
    }

    /**
     * Returns the bounds of the area covered by this node's primitive paint.
     */
    public Rectangle2D getPrimitiveBounds() {
        return bounds;
    }

    public Rectangle2D getGeometryBounds(){
        return bounds;
    }

    public Rectangle2D getSensitiveBounds(){
        return bounds;
    }

    /**
     * Returns the outline of this node.
     */
    public Shape getOutline() {
        return bounds;
    }

    public GraphicsNode getGraphicsNode(int idx) {
        // System.err.println("Getting: " + idx);
        if (srcs[idx] != null) {
            Object o = srcs[idx].get();
            if (o != null) 
                return (GraphicsNode)o;
        }
        
        try {
            SVGDocument svgDoc = (SVGDocument)loader.loadDocument
                (srcURLs[idx].toString());

            GraphicsNode gn;
            gn = createSVGImageNode(ctx, multiImgElem, 
                                    bounds, svgDoc);
            srcs[idx] = new SoftReference(gn);
            return gn;
        } catch (Exception ex) { /* ex.printStackTrace(); */ }

        try {
            GraphicsNode gn;
            gn = createRasterImageNode(ctx, multiImgElem, 
                                       bounds, srcURLs[idx]);
            srcs[idx] = new SoftReference(gn);
            return gn;
        } catch (Exception ex) { /* ex.printStackTrace(); */ }

        return null;
    }


    /**
     * Returns a GraphicsNode that represents an raster image in JPEG or PNG
     * format.
     *
     * @param ctx the bridge context
     * @param e the image element
     * @param uriStr the uri of the image
     */
    protected static GraphicsNode createRasterImageNode(BridgeContext ctx,
                                                        Element e,
                                                        Rectangle2D bounds,
                                                        ParsedURL     purl) {

        RasterImageNode node = new RasterImageNode();

        ImageTagRegistry reg = ImageTagRegistry.getRegistry();
        Filter           img = reg.readURL(purl);
        Object           obj = img.getProperty
            (SVGBrokenLinkProvider.SVG_BROKEN_LINK_DOCUMENT_PROPERTY);
        if ((obj != null) && (obj instanceof SVGDocument)) {
            // Ok so we are dealing with a broken link.
            return createSVGImageNode(ctx, e, bounds, (SVGDocument)obj);
        }
        node.setImage(img);
        Rectangle2D imgBounds = img.getBounds2D();

        // create the implicit viewBox for the raster image. The viewBox for a
        // raster image is the size of the image
        float [] vb = new float[4];
        vb[0] = 0; // x
        vb[1] = 0; // y
        vb[2] = (float)imgBounds.getWidth(); // width
        vb[3] = (float)imgBounds.getHeight(); // height

        // System.err.println("Bounds: " + bounds);
        // System.err.println("ImgB: " + imgBounds);
        // handles the 'preserveAspectRatio', 'overflow' and 'clip' and 
        // sets the appropriate AffineTransform to the image node
        initializeViewport(e, node, vb, bounds);

        return node;
    }

    /**
     * Returns a GraphicsNode that represents a svg document as an image.
     *
     * @param ctx the bridge context
     * @param e the image element
     * @param bounds the bounds for this graphicsNode
     * @param imgDocument the SVG document that represents the image
     */
    protected static GraphicsNode createSVGImageNode(BridgeContext ctx,
                                                     Element e,
                                                     Rectangle2D bounds,
                                                     SVGDocument imgDocument) {

        CompositeGraphicsNode result = new CompositeGraphicsNode();

        Rectangle2D r = CSSUtilities.convertEnableBackground(e);
        if (r != null) {
            result.setBackgroundEnable(r);
        }

        SVGSVGElement svgElement = imgDocument.getRootElement();
        GVTBuilder builder = new GVTBuilder();
        GraphicsNode node = builder.build(ctx, imgDocument);
        // HACK: remove the clip set by the SVGSVGElement as the overflow
        // and clip properties must be ignored. The clip will be set later
        // using the overflow and clip of the <image> element.
        node.setClip(null);
        result.getChildren().add(node);

        // create the implicit viewBox for the SVG image. The viewBox
        // for a SVG image is the viewBox of the outermost SVG element
        // of the SVG file
        String viewBox =
            svgElement.getAttributeNS(null, SVG_VIEW_BOX_ATTRIBUTE);
        float [] vb = ViewBox.parseViewBoxAttribute(e, viewBox);
        
        // handles the 'preserveAspectRatio', 'overflow' and 'clip' and sets 
        // the appropriate AffineTransform to the image node

        // System.err.println("Bounds: " + bounds);
        // System.err.println("ViewBox: " + viewBox);
        initializeViewport(e, result, vb, bounds);

        return result;
    }

    /**
     * Initializes according to the specified element, the specified graphics
     * node with the specified bounds. This method takes into account the
     * 'viewBox', 'preserveAspectRatio', and 'clip' properties. According to
     * those properties, a AffineTransform and a clip is set.
     *
     * @param e the image element that defines the properties
     * @param node the graphics node
     * @param vb the implicit viewBox definition
     * @param bounds the bounds of the image element
     */
    protected static void initializeViewport(Element e,
                                             GraphicsNode node,
                                             float [] vb,
                                             Rectangle2D bounds) {

        float x = (float)bounds.getX();
        float y = (float)bounds.getY();
        float w = (float)bounds.getWidth();
        float h = (float)bounds.getHeight();

        AffineTransform at
            = ViewBox.getPreserveAspectRatioTransform(e, vb, w, h);
        // System.err.println("VP Affine: " + at);
        at.preConcatenate(AffineTransform.getTranslateInstance(x, y));
        node.setTransform(at);

        // 'overflow' and 'clip'
        Shape clip = null;
        if (CSSUtilities.convertOverflow(e)) { // overflow:hidden
            float [] offsets = CSSUtilities.convertClip(e);
            if (offsets == null) { // clip:auto
                clip = new Rectangle2D.Float(x, y, w, h);
            } else { // clip:rect(<x> <y> <w> <h>)
                // offsets[0] = top
                // offsets[1] = right
                // offsets[2] = bottom
                // offsets[3] = left
                clip = new Rectangle2D.Float(x+offsets[3],
                                             y+offsets[0],
                                             w-offsets[1]-offsets[3],
                                             h-offsets[2]-offsets[0]);
            }
        }

        if (clip != null) {
            try {
                at = at.createInverse(); // clip in user space
                Filter filter = node.getGraphicsNodeRable(true);
                clip = at.createTransformedShape(clip);
                node.setClip(new ClipRable8Bit(filter, clip));
            } catch (java.awt.geom.NoninvertibleTransformException ex) {}
        }
    }
}    

