/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tangxiaolv.telegramgallery.exoplayer2.extractor.ogg;

import com.tangxiaolv.telegramgallery.exoplayer2.C;
import com.tangxiaolv.telegramgallery.exoplayer2.ParserException;
import com.tangxiaolv.telegramgallery.exoplayer2.extractor.Extractor;
import com.tangxiaolv.telegramgallery.exoplayer2.extractor.ExtractorInput;
import com.tangxiaolv.telegramgallery.exoplayer2.extractor.ExtractorOutput;
import com.tangxiaolv.telegramgallery.exoplayer2.extractor.ExtractorsFactory;
import com.tangxiaolv.telegramgallery.exoplayer2.extractor.PositionHolder;
import com.tangxiaolv.telegramgallery.exoplayer2.extractor.TrackOutput;
import com.tangxiaolv.telegramgallery.exoplayer2.util.ParsableByteArray;
import java.io.IOException;

/**
 * Ogg {@link Extractor}.
 */
public class OggExtractor implements Extractor {

  /**
   * Factory for {@link OggExtractor} instances.
   */
  public static final ExtractorsFactory FACTORY = new ExtractorsFactory() {

    @Override
    public Extractor[] createExtractors() {
      return new Extractor[] {new OggExtractor()};
    }

  };

  private static final int MAX_VERIFICATION_BYTES = 8;

  private StreamReader streamReader;

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    try {
      OggPageHeader header = new OggPageHeader();
      if (!header.populate(input, true) || (header.type & 0x02) != 0x02) {
        return false;
      }

      int length = Math.min(header.bodySize, MAX_VERIFICATION_BYTES);
      ParsableByteArray scratch = new ParsableByteArray(length);
      input.peekFully(scratch.data, 0, length);

      if (FlacReader.verifyBitstreamType(resetPosition(scratch))) {
        streamReader = new FlacReader();
      } else if (VorbisReader.verifyBitstreamType(resetPosition(scratch))) {
        streamReader = new VorbisReader();
      } else if (OpusReader.verifyBitstreamType(resetPosition(scratch))) {
        streamReader = new OpusReader();
      } else {
        return false;
      }
      return true;
    } catch (ParserException e) {
      return false;
    }
  }

  @Override
  public void init(ExtractorOutput output) {
    TrackOutput trackOutput = output.track(0, C.TRACK_TYPE_AUDIO);
    output.endTracks();
    // TODO: fix the case if sniff() isn't called
    streamReader.init(output, trackOutput);
  }

  @Override
  public void seek(long position, long timeUs) {
    streamReader.seek(position, timeUs);
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    return streamReader.read(input, seekPosition);
  }

  //@VisibleForTesting
  /* package */ StreamReader getStreamReader() {
    return streamReader;
  }

  private static ParsableByteArray resetPosition(ParsableByteArray scratch) {
    scratch.setPosition(0);
    return scratch;
  }

}
