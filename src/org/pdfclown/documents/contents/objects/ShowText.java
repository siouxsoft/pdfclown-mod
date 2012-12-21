/*
  Copyright 2007-2011 Stefano Chizzolini. http://www.pdfclown.org

  Contributors:
    * Stefano Chizzolini (original code developer, http://www.stefanochizzolini.it)

  This file should be part of the source code distribution of "PDF Clown library"
  (the Program): see the accompanying README files for more info.

  This Program is free software; you can redistribute it and/or modify it under the terms
  of the GNU Lesser General Public License as published by the Free Software Foundation;
  either version 3 of the License, or (at your option) any later version.

  This Program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY,
  either expressed or implied; without even the implied warranty of MERCHANTABILITY or
  FITNESS FOR A PARTICULAR PURPOSE. See the License for more details.

  You should have received a copy of the GNU Lesser General Public License along with this
  Program (see README files); if not, go to the GNU website (http://www.gnu.org/licenses/).

  Redistribution and use, with or without modification, are permitted provided that such
  redistributions retain the above copyright notice, license and disclaimer, along with
  this list of conditions.
*/

package org.pdfclown.documents.contents.objects;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;

import org.pdfclown.PDF;
import org.pdfclown.VersionEnum;
import org.pdfclown.documents.contents.ContentScanner;
import org.pdfclown.documents.contents.ContentScanner.GraphicsState;
import org.pdfclown.documents.contents.IContentContext;
import org.pdfclown.documents.contents.fonts.Font;
import org.pdfclown.documents.contents.fonts.Type1Font;
import org.pdfclown.objects.PdfDirectObject;

/**
  Abstract 'show a text string' operation [PDF:1.6:5.3.2].

  @author Stefano Chizzolini (http://www.stefanochizzolini.it)
  @since 0.0.8
  @version 0.1.1, 11/01/11
*/
@PDF(VersionEnum.PDF10)
public abstract class ShowText
  extends Operation
{
  // <class>
  // <interfaces>
  public interface IScanner
  {
    /**
      Notifies the scanner about a text character.

      @param textChar Scanned character.
      @param textCharBox Bounding box of the scanned character.
    */
    void scanChar(
      char textChar,
      Rectangle2D textCharBox
      );
  }
  // </interfaces>

  // <dynamic>
  // <constructors>
  protected ShowText(
    String operator
    )
  {super(operator);}

  protected ShowText(
    String operator,
    PdfDirectObject... operands
    )
  {super(operator, operands);}

  protected ShowText(
    String operator,
    List<PdfDirectObject> operands
    )
  {super(operator, operands);}
  // </constructors>

  // <interface>
  // <public>
  /**
    Gets the encoded text.
    <h3>Remarks</h3>
    <p>Text is expressed in native encoding: to resolve it to Unicode, pass it
    to the decode method of the corresponding font.</p>
  */
  public abstract byte[] getText(
    );

  /**
    Gets the encoded text elements along with their adjustments.
    <h3>Remarks</h3>
    <p>Text is expressed in native encoding: to resolve it to Unicode, pass it
    to the decode method of the corresponding font.</p>

    @return Each element can be either a byte array or a number:
      if it's a byte array (encoded text), the operator shows text glyphs;
      if it's a number (glyph adjustment), the operator inversely adjusts the next glyph position by that amount
      (that is: a positive value reduces the distance between consecutive glyphs).
  */
  public List<Object> getValue(
    )
  {return Arrays.asList((Object)getText());}

  @Override
  public void scan(
    GraphicsState state
    )
  {
	  scan(state, null);
  }

  /**
    Executes scanning on this operation.

    @param state Graphics state context.
    @param textScanner Scanner to be notified about text contents.
      In case it's null, the operation is applied to the graphics state context.
  */
  public void scan(
    ContentScanner.GraphicsState state,
    IScanner textScanner
    )
  {
    /*
      TODO: I really dislike this solution -- it's a temporary hack until the event-driven
      parsing mechanism is implemented...
     */
    /*
      TODO: support to vertical writing mode.
    */

    IContentContext context = state.getScanner().getContentContext();
    double contextHeight = context.getBox().getHeight();
    Font font = state.getFont();
    double fontSize = state.getFontSize();
    double rise = state.getRise();
    double scale = state.getScale() / 100;
    double scaledFactor = Font.getScalingFactor(fontSize) * scale;
    double wordSpace = state.getWordSpace() * scale;
    double charSpace = state.getCharSpace() * scale;
    AffineTransform ctm = (AffineTransform)state.getCtm().clone();
    AffineTransform tm;
    if(this instanceof ShowTextToNextLine)
    {
      ShowTextToNextLine showTextToNextLine = (ShowTextToNextLine)this;
      Double newWordSpace = showTextToNextLine.getWordSpace();
      if(newWordSpace != null)
      {
        if(textScanner == null)
        {state.setWordSpace(newWordSpace);}
        wordSpace = newWordSpace * scale;
      }
      Double newCharSpace = showTextToNextLine.getCharSpace();
      if(newCharSpace != null)
      {
        if(textScanner == null)
        {state.setCharSpace(newCharSpace);}
        charSpace = newCharSpace * scale;
      }
      TranslateTextToNextLine.Value.scan(state);
      //tm = (AffineTransform)state.getTlm().clone();
      //tm.translate(0, state.getLead());
    }
    //tm = (AffineTransform)state.getTm().clone();
    tm = new AffineTransform(state.getTm());

    //Loop through text elements
    for(Object textElement : getValue())
    {
      if(textElement instanceof byte[]) // Text string.
      {
        String textString = font.decode((byte[])textElement);
        for(char textChar : textString.toCharArray())
        {
        	double charWidth = font.getWidth(textChar) * scaledFactor;
          if(textScanner != null)
          {
            AffineTransform trm = (AffineTransform)ctm.clone(); trm.concatenate(tm);
            double charHeight = font.getHeight(textChar,fontSize);
            Rectangle2D charBox = new Rectangle2D.Double(
              trm.getTranslateX(),
              contextHeight - trm.getTranslateY() - font.getAscent(fontSize) * trm.getScaleY(),
              charWidth * trm.getScaleX(),
              charHeight * trm.getScaleY()
              );
            /*
            NOTE: The text rendering matrix is recomputed before each glyph is painted
            during a text-showing operation.
             */
            textScanner.scanChar(textChar,charBox);
            //.drawString(new Character(textChar).toString(), new System.Drawing.Font("Arial", 4), System.Drawing.Brushes.Black, charBox);
          } else {
          	/*
          	//AffineTransform scaleM = new AffineTransform(fontSize * scale, 0, 0, fontSize, 0, rise);
          	//AffineTransform trm = (AffineTransform)tm.clone(); trm.concatenate(scaleM);
          	AffineTransform trm = (AffineTransform)ctm.clone(); trm.concatenate(tm);
						double charHeight = font.getHeight(textChar, fontSize);
						Rectangle2D charBox = new Rectangle2D.Double(trm.getTranslateX(),
								contextHeight - trm.getTranslateY() - font.getAscent(fontSize)
										* trm.getScaleY(), charWidth * trm.getScaleX(), charHeight
										* trm.getScaleY());
						java.awt.Font defFont = java.awt.Font.getFont("Arial");
						Graphics2D g = state.getScanner().getRenderContext();
						AffineTransform curr = (AffineTransform) g.getTransform().clone();
						//System.out.println(charBox);
						g.transform(ctm);
						g.setFont(defFont);
						
						
						g.setPaint(Color.BLACK);
						//g.drawString(new Character(textChar).toString(), (float)trm.getTranslateX(), (float)charBox.getY());
						//g.drawString(new Character(textChar).toString(), 0, 0);
						// g.drawString(new Character(textChar).toString(),
						// (int)tm.getTranslateX(), (int)tm.getTranslateY());
						// g.drawString("H", (float)charBox.getX(), (float)charBox.getY());
						g.setTransform(curr);
						*/
          }
          
          //java.awt.Font javaFont = java.awt.Font.createFont(java.awt.Font.TYPE1_FONT, font.)
          if(font instanceof Type1Font) {
          	double charHeight = font.getHeight(textChar, fontSize);
          	AffineTransform trm = (AffineTransform)ctm.clone(); trm.concatenate(tm);
          	Rectangle2D charBox = new Rectangle2D.Double(trm.getTranslateX(),
								contextHeight - trm.getTranslateY() - font.getAscent(fontSize)
										* trm.getScaleY(), charWidth * trm.getScaleX(), charHeight
										* trm.getScaleY());
          	Graphics2D g = state.getScanner().getRenderContext();
          	Type1Font font1 = (Type1Font)font;
          	GlyphVector gv = font1.getGlyphVector(g.getFontRenderContext(), new Character(textChar).toString());
          	double normalWidth = gv.getVisualBounds().getWidth();
          	double normalHeight = gv.getVisualBounds().getWidth();
          	double widthRatio = charWidth / normalWidth;
          	//glyph space
          	AffineTransform glyphTM = AffineTransform.getScaleInstance(widthRatio, -charHeight/normalHeight);
          	Shape thatShape = glyphTM.createTransformedShape(gv.getGlyphOutline(0));
          	//TODO concatenate to improve perf
          	//text space
          	thatShape = tm.createTransformedShape(thatShape);
          	//gv.getOutline().
          	//g.transform(tm);
          	AffineTransform curr = (AffineTransform)g.getTransform().clone();
          	//g.transform(ctm);
          	g.setColor(java.awt.Color.black);
          	g.fill(thatShape);
          	//g.draw(gv.getOutline((float)trm.getTranslateX(), (float)charBox.getY()));
          	//g.drawGlyphVector(gv, (float)trm.getTranslateX(), (float)charBox.getY());
          	//g.setTransform(curr);
          }

          /*
            NOTE: After the glyph is painted, the text matrix is updated
            according to the glyph displacement and any applicable spacing parameter.
          */
          tm.translate(charWidth + charSpace + (textChar == ' ' ? wordSpace : 0), 0);
        }
      }
      else // Text position adjustment. WHAT IS THIS?
      {tm.translate(-((Number)textElement).doubleValue() * scaledFactor, 0);}
    }

    if(textScanner == null)
    {
      state.setTm(tm);

      if(this instanceof ShowTextToNextLine)
      {state.setTlm((AffineTransform)tm.clone());}
    }
  }

  /**
    @see #getText()
  */
  public abstract void setText(
    byte[] value
    );

  /**
    @see #getValue()
  */
  public void setValue(
    List<Object> value
    )
  {setText((byte[])value.get(0));}
  // </public>
  // </interface>
  // </dynamic>
  // </class>
}