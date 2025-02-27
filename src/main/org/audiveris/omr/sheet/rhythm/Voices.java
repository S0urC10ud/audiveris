//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                           V o i c e s                                          //
//                                                                                                //
//------------------------------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">
//
//  Copyright © Audiveris 2021. All rights reserved.
//
//  This program is free software: you can redistribute it and/or modify it under the terms of the
//  GNU Affero General Public License as published by the Free Software Foundation, either version
//  3 of the License, or (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
//  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//  See the GNU Affero General Public License for more details.
//
//  You should have received a copy of the GNU Affero General Public License along with this
//  program.  If not, see <http://www.gnu.org/licenses/>.
//------------------------------------------------------------------------------------------------//
// </editor-fold>
package org.audiveris.omr.sheet.rhythm;

import java.awt.Color;
import org.audiveris.omr.score.LogicalPart;
import org.audiveris.omr.score.Page;
import org.audiveris.omr.score.PageRef;
import org.audiveris.omr.score.Score;
import org.audiveris.omr.sheet.Book;
import org.audiveris.omr.sheet.SheetStub;
import org.audiveris.omr.sheet.Part;
import org.audiveris.omr.sheet.SystemInfo;
import org.audiveris.omr.sheet.rhythm.Voice.Family;
import org.audiveris.omr.sig.SIGraph;
import org.audiveris.omr.sig.inter.AbstractChordInter;
import org.audiveris.omr.sig.inter.HeadInter;
import org.audiveris.omr.sig.inter.Inter;
import org.audiveris.omr.sig.inter.Inters;
import org.audiveris.omr.sig.inter.SlurInter;
import org.audiveris.omr.sig.relation.Relation;
import org.audiveris.omr.sig.relation.SameVoiceRelation;
import org.audiveris.omr.sig.relation.SlurHeadRelation;
import static org.audiveris.omr.util.HorizontalSide.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Class {@code Voices} connects voices and harmonizes their IDs (and thus colors)
 * within a stack, a system, a page or a score.
 *
 * @author Hervé Bitteur
 */
public abstract class Voices
{
    //~ Static fields/initializers -----------------------------------------------------------------

    private static final Logger logger = LoggerFactory.getLogger(Voices.class);

    /** To sort voices by their ID. */
    public static final Comparator<Voice> byId = (Voice v1, Voice v2)
            -> Integer.compare(v1.getId(), v2.getId());

    /** To sort voices by vertical position within their containing measure or stack. */
    public static final Comparator<Voice> byOrdinate = (Voice v1, Voice v2) -> {
        if (v1.getMeasure().getStack() != v2.getMeasure().getStack()) {
            throw new IllegalArgumentException("Comparing voices in different stacks");
        }

        // Check if they are located in different parts
        Part p1 = v1.getMeasure().getPart();
        Part p2 = v2.getMeasure().getPart();

        if (p1 != p2) {
            return Part.byId.compare(p1, p2);
        }

        // Check voice family
        Family f1 = v1.getFamily();
        Family f2 = v2.getFamily();

        if (f1 != f2) {
            return f1.compareTo(f2);
        }

        AbstractChordInter c1 = v1.getFirstChord();
        AbstractChordInter c2 = v2.getFirstChord();
        Slot firstSlot1 = c1.getSlot();
        Slot firstSlot2 = c2.getSlot();

        // Check if the voices started in different time slots
        // Beware of whole rests, they have no time slot
        if ((firstSlot1 != null) && (firstSlot2 != null)) {
            int comp = Integer.compare(firstSlot1.getId(), firstSlot2.getId());

            if (comp != 0) {
                return comp;
            }

            // Same first time slot, so let's use chord ordinate
            return Inters.byOrdinate.compare(c1, c2);
        } else {
            // We have at least one whole rest (which always starts on slot 1, by definition)
            if ((firstSlot2 != null) && (firstSlot2.getId() > 1)) {
                return -1;
            }

            if ((firstSlot1 != null) && (firstSlot1.getId() > 1)) {
                return 1;
            }

            // Both are at beginning of measure, so let's use chord ordinates
            return Inters.byOrdinate.compare(c1, c2);
        }
    };

    /** Sequence of colors for voices. */
    private static final int alpha = 200;

    private static final Color[] voiceColors = new Color[]{
        /** 1 Purple */
        new Color(128, 64, 255, alpha),
        /** 2 Green */
        new Color(0, 255, 0, alpha),
        /** 3 Brown */
        new Color(165, 42, 42, alpha),
        /** 4 Magenta */
        new Color(255, 0, 255, alpha),
        /** 5 Cyan */
        new Color(0, 255, 255, alpha),
        /** 6 Orange */
        new Color(255, 200, 0, alpha),
        /** 7 Pink */
        new Color(255, 150, 150, alpha),
        /** 8 BlueGreen */
        new Color(0, 128, 128, alpha)};

    //~ Constructors -------------------------------------------------------------------------------
    // Not meant to be instantiated.
    private Voices ()
    {
    }

    //~ Methods ------------------------------------------------------------------------------------
    //---------//
    // colorOf //
    //---------//
    /**
     * Report the color to use when painting elements related to the provided voice.
     *
     * @param voice the provided voice
     * @return the color to use
     */
    public static Color colorOf (Voice voice)
    {
        return colorOf(voice.getId());
    }

    //---------//
    // colorOf //
    //---------//
    /**
     * Report the color to use when painting elements related to the provided voice ID.
     *
     * @param id the provided voice id
     * @return the color to use
     */
    public static Color colorOf (int id)
    {
        // Use table of colors, circularly.
        int index = (id - 1) % voiceColors.length;

        return voiceColors[index];
    }

    //---------------//
    // getColorCount //
    //---------------//
    /**
     * Report the number of defined voice colors.
     *
     * @return count of colors
     */
    public static int getColorCount ()
    {
        return voiceColors.length;
    }

    //------------//
    // refinePage //
    //------------//
    /**
     * Connect voices within the same logical part across all systems of a page.
     *
     * @param page the page to process
     */
    public static void refinePage (Page page)
    {
        logger.debug("PageStep.refinePage");

        final SystemInfo firstSystem = page.getFirstSystem();

        // Across systems within a single page, the partnering slur is the left extension
        final SlurAdapter systemSlurAdapter = (SlurInter slur) -> slur.getExtension(LEFT);

        for (LogicalPart logicalPart : page.getLogicalParts()) {
            for (SystemInfo system : page.getSystems()) {
                Part part = system.getPartById(logicalPart.getId());

                if (part != null) {
                    if (system != firstSystem) {
                        // Check tied voices from previous system
                        final Measure firstMeasure = part.getFirstMeasure();

                        // A part may have no measure (case of tablature, which are ignored today)
                        if (firstMeasure != null) {
                            for (Voice voice : firstMeasure.getVoices()) {
                                Integer tiedId = getTiedId(voice, systemSlurAdapter);

                                if ((tiedId != null) && (voice.getId() != tiedId)) {
                                    part.swapVoiceId(voice.getId(), tiedId);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    //-------------//
    // refineScore //
    //-------------//
    /**
     * Connect voices within the same logical part across all pages of a score.
     * <p>
     * Ties across sheets cannot easily be persisted, so we detect and use them on the fly.
     *
     * @param score the score to process
     * @param stubs the valid selected stubs
     * @return the count of modifications made
     */
    public static int refineScore (Score score,
                                   List<SheetStub> stubs)
    {
        final Book book = score.getBook();
        int modifs = 0;
        SystemInfo prevSystem = null; // Last system of preceding page, if any

        for (int pageNumber = 1; pageNumber <= score.getPageCount(); pageNumber++) {
            // Within valid selected stubs?
            final PageRef ref = score.getPageRefs().get(pageNumber - 1);
            final SheetStub stub = book.getStub(ref.getSheetNumber());

            if (!stubs.contains(stub)) {
                prevSystem = null;
                continue;
            }

            final Page page = score.getPage(pageNumber);

            if (prevSystem != null) {
                for (LogicalPart scorePart : score.getLogicalParts()) {
                    // Check tied voices from same logicalPart in previous page
                    final LogicalPart logicalPart = page.getLogicalPartById(scorePart.getId());

                    if (logicalPart == null) {
                        continue; // logical part not found in this page
                    }

                    final Part part = page.getFirstSystem().getPartById(logicalPart.getId());

                    if (part == null) {
                        continue; // logical part not found in the first system of this page
                    }

                    final List<SlurInter> orphans = part.getSlurs(SlurInter.isBeginningOrphan);

                    final Part precedingPart = prevSystem.getPartById(logicalPart.getId());

                    if (precedingPart != null) {
                        final List<SlurInter> precOrphans = precedingPart.getSlurs(
                                SlurInter.isEndingOrphan);

                        final Map<SlurInter, SlurInter> links = part.getCrossSlurLinks(
                                precedingPart); // Links: Slur -> prevSlur

                        // Apply the links possibilities
                        for (Map.Entry<SlurInter, SlurInter> entry : links.entrySet()) {
                            final SlurInter slur = entry.getKey();
                            final SlurInter prevSlur = entry.getValue();

                            slur.checkCrossTie(prevSlur);
                        }

                        // Purge orphans across pages
                        orphans.removeAll(links.keySet());
                        precOrphans.removeAll(links.values());
                        SlurInter.discardOrphans(precOrphans, RIGHT);

                        // Across pages within a score, use the links map
                        final SlurAdapter pageSlurAdapter = (SlurInter slur) -> links.get(slur);

                        for (Voice voice : part.getFirstMeasure().getVoices()) {
                            Integer tiedId = getTiedId(voice, pageSlurAdapter);

                            if ((tiedId != null) && (voice.getId() != tiedId)) {
                                logicalPart.swapVoiceId(page, voice.getId(), tiedId);
                                modifs++;
                            }
                        }
                    }

                    SlurInter.discardOrphans(orphans, LEFT);
                }
            }

            prevSystem = page.getLastSystem();
        }

        return modifs;
    }

    //-------------//
    // refineStack //
    //-------------//
    /**
     * Refine voice IDs within a stack. (METHOD NOT USED)
     * <p>
     * When this method is called, initial IDs have been assigned according to voice creation
     * (measure-long voices first, then slot voices, with each voice remaining in its part).
     * <p>
     * Here we simply rename the IDs from top to bottom (roughly), within each staff.
     * <p>
     * We then extend each chord voice to its preceding cue chords.
     *
     * @param stack the stack to process
     */
    public static void refineStack (MeasureStack stack)
    {
        // Within each measure, sort voices vertically and rename them accordingly per staff.
        for (Measure measure : stack.getMeasures()) {
            measure.sortVoices();
            measure.renameVoices();
            measure.setCueVoices();
        }
    }

    //--------------//
    // refineSystem //
    //--------------//
    /**
     * Connect voices within the same part across all measures of a system.
     * <p>
     * When this method is called, each stack has a sequence of voices, the goal is now to
     * connect them from one stack to the other.
     *
     * @param system the system to process
     */
    public static void refineSystem (SystemInfo system)
    {
        final SIGraph sig = system.getSig();

        // Across measures within a single system, the partnering slur is the slur itself
        final SlurAdapter measureSlurAdapter = (SlurInter slur) -> slur;

        for (Part part : system.getParts()) {
            Measure prevMeasure = null;

            for (MeasureStack stack : system.getStacks()) {
                final Measure measure = stack.getMeasureAt(part);
                final List<Voice> measureVoices = measure.getVoices(); // Sorted vertically (?)

                for (Voice voice : measureVoices) {
                    if (prevMeasure != null) {
                        // Check voices from same part in previous measure
                        // Tie-based voice link
                        final Integer tiedId = getTiedId(voice, measureSlurAdapter);

                        if ((tiedId != null) && (voice.getId() != tiedId)) {
                            measure.swapVoiceId(voice, tiedId);
                        }

                        // SameVoiceRelation-based voice link
                        final AbstractChordInter ch2 = voice.getFirstChord();

                        if (ch2 != null) {
                            for (Relation rel : sig.getRelations(ch2, SameVoiceRelation.class)) {
                                final Inter inter = sig.getOppositeInter(ch2, rel);
                                final AbstractChordInter ch1 = (AbstractChordInter) inter;

                                if (ch1.getMeasure() == prevMeasure) {
                                    if (voice.getId() != ch1.getVoice().getId()) {
                                        measure.swapVoiceId(voice, ch1.getVoice().getId());
                                    }

                                    break;
                                }
                            }
                        }
                    }

                    // Preferred voice IDs?
                    final AbstractChordInter ch1 = voice.getFirstChord();

                    if (ch1 != null) {
                        final Integer preferredVoiceId = ch1.getPreferredVoiceId();

                        if ((preferredVoiceId != null) && (preferredVoiceId != voice.getId())) {
                            measure.swapVoiceId(voice, preferredVoiceId);
                        }
                    }
                }

                prevMeasure = measure;
            }
        }
    }

    //-----------//
    // getTiedId //
    //-----------//
    /**
     * Check whether the provided voice is tied (via a tie slur) to a previous voice
     * and thus must use the same ID.
     *
     * @param voice       the voice to check
     * @param slurAdapter to provide the linked slur at previous location
     * @return the imposed ID if any, null otherwise
     */
    private static Integer getTiedId (Voice voice,
                                      SlurAdapter slurAdapter)
    {
        final AbstractChordInter firstChord = voice.getFirstChord();

        if (firstChord == null) {
            return null;
        }

        final SIGraph sig = firstChord.getSig();

        // Is there an incoming tie on a head of this chord?
        for (Inter note : firstChord.getNotes()) {
            if (note instanceof HeadInter) {
                for (Relation r : sig.getRelations(note, SlurHeadRelation.class)) {
                    SlurHeadRelation shRel = (SlurHeadRelation) r;

                    if (shRel.getSide() == RIGHT) {
                        SlurInter slur = (SlurInter) sig.getOppositeInter(note, r);

                        if (slur.isTie()) {
                            SlurInter prevSlur = slurAdapter.getInitialSlur(slur);

                            if (prevSlur != null) {
                                HeadInter left = prevSlur.getHead(LEFT);

                                if (left != null) {
                                    final Voice leftVoice = left.getVoice();
                                    logger.debug("{} ties {} over to {}", slur, voice, leftVoice);

                                    // Can be null if rhythm could not process the whole measure
                                    if (leftVoice != null) {
                                        return leftVoice.getId();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    //~ Inner Interfaces ---------------------------------------------------------------------------
    //-------------//
    // SlurAdapter //
    //-------------//
    /**
     * This adapter gives access to the partnering slur of a given slur.
     * <p>
     * There are different implementations:
     * <ul>
     * <li>measureSlurAdapter: Across measures in a single system.
     * <li>systemSlurAdapter: Across systems in a single page.
     * <li>pageSlurAdapter: Across pages in a (single) score.
     * </ul>
     */
    private static interface SlurAdapter
    {

        /**
         * Report the initial (that is: before) partnering slur.
         *
         * @param slur the slur to follow
         * @return the extending slur (or the slur itself)
         */
        SlurInter getInitialSlur (SlurInter slur);
    }
}
