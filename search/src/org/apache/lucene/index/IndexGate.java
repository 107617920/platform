/*
    IndexGate inspects the capabilities of the provided Lucene index directory to determine the index format. This file
    was extracted from a preliminary version of Luke (a Lucene index inspection tool) 4.2.0 and subsetted to the single
    public method that we care about. Andrzej Bialecki is apparently the primary author and the tool is clearly
    distributed under the Apache 2.0 license.

    Copyright Andrzej Bialecki

    Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.index;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;

import java.io.IOException;
import java.util.HashMap;

/**
 * This class allows us to peek at various Lucene internals, not available
 * through public APIs (for good reasons, but inquiring minds want to know ...).
 * 
 * @author ab
 *
 */
public class IndexGate {
  static HashMap<String, String> knownExtensions = new HashMap<>();

  // old version constants
  public static final int OLD_FORMAT = -1;

  /** This format adds details used for lockless commits.  It differs
   * slightly from the previous format in that file names
   * are never re-used (write once).  Instead, each file is
   * written to the next generation.  For example,
   * segments_1, segments_2, etc.  This allows us to not use
   * a commit lock.  See <a
   * href="http://lucene.apache.org/java/docs/fileformats.html">file
   * formats</a> for details.
   */
  public static final int FORMAT_LOCKLESS = -2;

  /** This format adds a "hasSingleNormFile" flag into each segment info.
   * See <a href="http://issues.apache.org/jira/browse/LUCENE-756">LUCENE-756</a>
   * for details.
   */
  public static final int FORMAT_SINGLE_NORM_FILE = -3;

  /** This format allows multiple segments to share a single
   * vectors and stored fields file. */
  public static final int FORMAT_SHARED_DOC_STORE = -4;

  /** This format adds a checksum at the end of the file to
   *  ensure all bytes were successfully written. */
  public static final int FORMAT_CHECKSUM = -5;

  /** This format adds the deletion count for each segment.
   *  This way IndexWriter can efficiently report numDocs(). */
  public static final int FORMAT_DEL_COUNT = -6;

  /** This format adds the boolean hasProx to record if any
   *  fields in the segment store prox information (ie, have
   *  omitTermFreqAndPositions==false) */
  public static final int FORMAT_HAS_PROX = -7;

  /** This format adds optional commit userData (String) storage. */
  public static final int FORMAT_USER_DATA = -8;

  /** This format adds optional per-segment String
   *  diagnostics storage, and switches userData to Map */
  public static final int FORMAT_DIAGNOSTICS = -9;

  /** Each segment records whether it has term vectors */
  public static final int FORMAT_HAS_VECTORS = -10;

  /** Each segment records the Lucene version that created it. */
  public static final int FORMAT_3_1 = -11;
  /** Some early 4.0 pre-alpha */
  public static final int FORMAT_PRE_4 = -12;

  static {
    knownExtensions.put(IndexFileNames.COMPOUND_FILE_EXTENSION, "compound file with various index data");
    knownExtensions.put(IndexFileNames.COMPOUND_FILE_ENTRIES_EXTENSION, "compound file entries list");
    knownExtensions.put(IndexFileNames.GEN_EXTENSION, "generation number - global file");
    knownExtensions.put(IndexFileNames.SEGMENTS, "per-commit list of segments and user data");
  }
  
  private static void detectOldFormats(FormatDetails res, int format) {
    switch (format) {
    case OLD_FORMAT:
      res.capabilities = "old plain";
      res.genericName = "Lucene Pre-2.1";
      res.version = "2.0?";
      break;
    case FORMAT_LOCKLESS:
      res.capabilities = "lock-less";
      res.genericName = "Lucene 2.1";
      res.version = "2.1";
      break;
    case FORMAT_SINGLE_NORM_FILE:
      res.capabilities = "lock-less, single norms file";
      res.genericName = "Lucene 2.2";
      res.version = "2.2";
      break;
    case FORMAT_SHARED_DOC_STORE:
      res.capabilities = "lock-less, single norms file, shared doc store";
      res.genericName = "Lucene 2.3";
      res.version = "2.3";
      break;
    case FORMAT_CHECKSUM:
      res.capabilities = "lock-less, single norms, shared doc store, checksum";
      res.genericName = "Lucene 2.4";
      res.version = "2.4";
      break;
    case FORMAT_DEL_COUNT:
      res.capabilities = "lock-less, single norms, shared doc store, checksum, del count";
      res.genericName = "Lucene 2.4";
      res.version = "2.4";
      break;
    case FORMAT_HAS_PROX:
      res.capabilities = "lock-less, single norms, shared doc store, checksum, del count, omitTf";
      res.genericName = "Lucene 2.4";
      res.version = "2.4";
      break;
    case FORMAT_USER_DATA:
      res.capabilities = "lock-less, single norms, shared doc store, checksum, del count, omitTf, user data";
      res.genericName = "Lucene 2.9-dev";
      res.version = "2.9-dev";
      break;
    case FORMAT_DIAGNOSTICS:
      res.capabilities = "lock-less, single norms, shared doc store, checksum, del count, omitTf, user data, diagnostics";
      res.genericName = "Lucene 2.9";
      res.version = "2.9";
      break;
    case FORMAT_HAS_VECTORS:
      res.capabilities = "lock-less, single norms, shared doc store, checksum, del count, omitTf, user data, diagnostics, hasVectors";
      res.genericName = "Lucene 2.9";
      res.version = "2.9";
      break;
    case FORMAT_3_1:
      res.capabilities = "lock-less, single norms, shared doc store, checksum, del count, omitTf, user data, diagnostics, hasVectors";
      res.genericName = "Lucene 3.1";
      res.version = "3.1";
      break;
    case FORMAT_PRE_4:
      res.capabilities = "flexible, unreleased 4.0 pre-alpha";
      res.genericName = "Lucene 4.0-dev";
      res.version = "4.0-dev";
      break;
    default:
      if (format < FORMAT_PRE_4) {
        res.capabilities = "flexible, unreleased 4.0 pre-alpha";
        res.genericName = "Lucene 4.0-dev";
        res.version = "4.0-dev";
      } else {
        res.capabilities = "unknown";
        res.genericName = "Lucene 1.3 or earlier, or unreleased";
        res.version = "1.3?";
      }
      break;
    }
    res.genericName = res.genericName + " (" + format + ")";    
  }
  
  public static FormatDetails getIndexFormat(final Directory dir) throws Exception {
    SegmentInfos.FindSegmentsFile fsf = new SegmentInfos.FindSegmentsFile(dir) {

      protected Object doBody(String segmentsFile) throws CorruptIndexException,
          IOException {
        FormatDetails res = new FormatDetails();
        res.capabilities = "unknown";
        res.genericName = "unknown";
        IndexInput in = dir.openInput(segmentsFile, IOContext.READ);
        try {
          int indexFormat = in.readInt();
          if (indexFormat == CodecUtil.CODEC_MAGIC) {
            res.genericName = "Lucene 4.x";
            res.capabilities = "flexible, codec-specific";
            int actualVersion = SegmentInfos.VERSION_40;
            try {
              actualVersion = CodecUtil.checkHeaderNoMagic(in, "segments", SegmentInfos.VERSION_40, Integer.MAX_VALUE);
              if (actualVersion > SegmentInfos.VERSION_40) {
                res.capabilities += " (WARNING: newer version of Lucene that this tool)";
              }
            } catch (Exception e) {
              e.printStackTrace();
              res.capabilities += " (error reading: " + e.getMessage() + ")";
            }
            res.genericName = "Lucene 4." + actualVersion;
            res.version = "4." + actualVersion;
          } else {
            res.genericName = "Lucene 3.x or prior";
            detectOldFormats(res, indexFormat);
            if (res.version.compareTo("3") < 0) {
              res.capabilities = res.capabilities + " (UNSUPPORTED)";
            }
          }
        } finally {
          in.close();          
        }
        return res;
      }
    };
    return (FormatDetails)fsf.run();
  }

  public static class FormatDetails {
    public String genericName = "N/A";
    public String capabilities = "N/A";
    public String version = "N/A";
  }
}
