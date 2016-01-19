package img_applet;

public class Mjpeg2jpeg_bsf {
	/*
	 * MJPEG/AVI1 to JPEG/JFIF bitstream format filter
	 * Copyright (c) 2010 Adrian Daerr and Nicolas George
	 *
	 * This file is part of FFmpeg.
	 *
	 * FFmpeg is free software; you can redistribute it and/or
	 * modify it under the terms of the GNU Lesser General Public
	 * License as published by the Free Software Foundation; either
	 * version 2.1 of the License, or (at your option) any later version.
	 *
	 * FFmpeg is distributed in the hope that it will be useful,
	 * but WITHOUT ANY WARRANTY; without even the implied warranty of
	 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
	 * Lesser General Public License for more details.
	 *
	 * You should have received a copy of the GNU Lesser General Public
	 * License along with FFmpeg; if not, write to the Free Software
	 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
	 */

	/*
	 * Adapted from mjpeg2jpeg.c, with original copyright:
	 * Paris 2010 Adrian Daerr, public domain
	 */

	static final byte jpeg_header[] = {
	    (byte)0xff, (byte)0xd8,         // SOI
	    (byte)0xff, (byte)0xe0,         // APP0
	    0x00, 0x10,                     // APP0 header size (including
	                                    // this field, but excluding preceding)
	    0x4a, 0x46, 0x49, 0x46, 0x00,   // ID string 'JFIF\0'
	    0x01, 0x01,                     // version
	    0x00,                           // bits per type
	    0x00, 0x00,                     // X density
	    0x00, 0x00,                     // Y density
	    0x00,                           // X thumbnail size
	    0x00,                           // Y thumbnail size
	};
	static final int jpeg_header_len = jpeg_header.length;

	static final int dht_segment_size = 420;
	static final byte dht_segment_head[] = { (byte)0xFF, (byte)0xC4, 0x01, (byte)0xA2, 0x00 };
	static final int dht_segment_head_len = dht_segment_head.length;
	static final byte dht_segment_frag[] = {
	    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
	    0x0a, 0x0b, 0x01, 0x00, 0x03, 0x01, 0x01, 0x01, 0x01, 0x01,
	    0x01, 0x01, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
	};
	static final int dht_segment_frag_len = dht_segment_frag.length;

	/*
	 * MJPEG encoder and decoder
	 * Copyright (c) 2000, 2001 Fabrice Bellard
	 * Copyright (c) 2003 Alex Beregszaszi
	 * Copyright (c) 2003-2004 Michael Niedermayer
	 *
	 * Support for external huffman table, various fixes (AVID workaround),
	 * aspecting, new decode_frame mechanism and apple mjpeg-b support
	 *                                  by Alex Beregszaszi
	 *
	 * This file is part of FFmpeg.
	 *
	 * FFmpeg is free software; you can redistribute it and/or
	 * modify it under the terms of the GNU Lesser General Public
	 * License as published by the Free Software Foundation; either
	 * version 2.1 of the License, or (at your option) any later version.
	 *
	 * FFmpeg is distributed in the hope that it will be useful,
	 * but WITHOUT ANY WARRANTY; without even the implied warranty of
	 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
	 * Lesser General Public License for more details.
	 *
	 * You should have received a copy of the GNU Lesser General Public
	 * License along with FFmpeg; if not, write to the Free Software
	 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
	 */

	/* Set up the standard Huffman tables (cf. JPEG standard section K.3) */
	/* IMPORTANT: these are only valid for 8-bit data precision! */
	static final byte ff_mjpeg_bits_dc_luminance[/*17*/] =
	{ /* 0-base */ 0, 0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0 };
	static final byte ff_mjpeg_val_dc[/*12*/] =
	{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };

	static final byte ff_mjpeg_bits_dc_chrominance[/*17*/] =
	{ /* 0-base */ 0, 0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0 };

	static final byte ff_mjpeg_bits_ac_luminance[/*17*/] =
	{ /* 0-base */ 0, 0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d };
	static final byte ff_mjpeg_val_ac_luminance[] =
	{ 0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12,
	  0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
	  0x22, 0x71, 0x14, 0x32, (byte)0x81, (byte)0x91, (byte)0xa1, 0x08,
	  0x23, 0x42, (byte)0xb1, (byte)0xc1, 0x15, 0x52, (byte)0xd1, (byte)0xf0,
	  0x24, 0x33, 0x62, 0x72, (byte)0x82, 0x09, 0x0a, 0x16,
	  0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
	  0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
	  0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
	  0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
	  0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
	  0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79,
	  0x7a, (byte)0x83, (byte)0x84, (byte)0x85, (byte)0x86, (byte)0x87, (byte)0x88, (byte)0x89,
	  (byte)0x8a, (byte)0x92, (byte)0x93, (byte)0x94, (byte)0x95, (byte)0x96, (byte)0x97, (byte)0x98,
	  (byte)0x99, (byte)0x9a, (byte)0xa2, (byte)0xa3, (byte)0xa4, (byte)0xa5, (byte)0xa6, (byte)0xa7,
	  (byte)0xa8, (byte)0xa9, (byte)0xaa, (byte)0xb2, (byte)0xb3, (byte)0xb4, (byte)0xb5, (byte)0xb6,
	  (byte)0xb7, (byte)0xb8, (byte)0xb9, (byte)0xba, (byte)0xc2, (byte)0xc3, (byte)0xc4, (byte)0xc5,
	  (byte)0xc6, (byte)0xc7, (byte)0xc8, (byte)0xc9, (byte)0xca, (byte)0xd2, (byte)0xd3, (byte)0xd4,
	  (byte)0xd5, (byte)0xd6, (byte)0xd7, (byte)0xd8, (byte)0xd9, (byte)0xda, (byte)0xe1, (byte)0xe2,
	  (byte)0xe3, (byte)0xe4, (byte)0xe5, (byte)0xe6, (byte)0xe7, (byte)0xe8, (byte)0xe9, (byte)0xea,
	  (byte)0xf1, (byte)0xf2, (byte)0xf3, (byte)0xf4, (byte)0xf5, (byte)0xf6, (byte)0xf7, (byte)0xf8,
	  (byte)0xf9, (byte)0xfa
	};

	static final byte ff_mjpeg_bits_ac_chrominance[/*17*/] =
	{ /* 0-base */ 0, 0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77 };

	static final byte ff_mjpeg_val_ac_chrominance[] =
	{ 0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21,
	  0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71,
	  0x13, 0x22, 0x32, (byte)0x81, 0x08, 0x14, 0x42, (byte)0x91,
	  (byte)0xa1, (byte)0xb1, (byte)0xc1, 0x09, 0x23, 0x33, 0x52, (byte)0xf0,
	  0x15, 0x62, 0x72, (byte)0xd1, 0x0a, 0x16, 0x24, 0x34,
	  (byte)0xe1, 0x25, (byte)0xf1, 0x17, 0x18, 0x19, 0x1a, 0x26,
	  0x27, 0x28, 0x29, 0x2a, 0x35, 0x36, 0x37, 0x38,
	  0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
	  0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
	  0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
	  0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78,
	  0x79, 0x7a, (byte)0x82, (byte)0x83, (byte)0x84, (byte)0x85, (byte)0x86, (byte)0x87,
	  (byte)0x88, (byte)0x89, (byte)0x8a, (byte)0x92, (byte)0x93, (byte)0x94, (byte)0x95, (byte)0x96,
	  (byte)0x97, (byte)0x98, (byte)0x99, (byte)0x9a, (byte)0xa2, (byte)0xa3, (byte)0xa4, (byte)0xa5,
	  (byte)0xa6, (byte)0xa7, (byte)0xa8, (byte)0xa9, (byte)0xaa, (byte)0xb2, (byte)0xb3, (byte)0xb4,
	  (byte)0xb5, (byte)0xb6, (byte)0xb7, (byte)0xb8, (byte)0xb9, (byte)0xba, (byte)0xc2, (byte)0xc3,
	  (byte)0xc4, (byte)0xc5, (byte)0xc6, (byte)0xc7, (byte)0xc8, (byte)0xc9, (byte)0xca, (byte)0xd2,
	  (byte)0xd3, (byte)0xd4, (byte)0xd5, (byte)0xd6, (byte)0xd7, (byte)0xd8, (byte)0xd9, (byte)0xda,
	  (byte)0xe2, (byte)0xe3, (byte)0xe4, (byte)0xe5, (byte)0xe6, (byte)0xe7, (byte)0xe8, (byte)0xe9,
	  (byte)0xea, (byte)0xf2, (byte)0xf3, (byte)0xf4, (byte)0xf5, (byte)0xf6, (byte)0xf7, (byte)0xf8,
	  (byte)0xf9, (byte)0xfa
	};

	static int append(byte[] dest, int destPos, final byte[] src, int srcPos, int length)
	{
	     System.arraycopy(src, srcPos, dest, destPos, length);
	     return destPos + length;
	}
	static int append(byte[] dest, int destPos, final byte[] src, int length)
	{
		return append(dest, destPos, src, 0, length);
	}
	
	static int append_dht_segment(byte[] buf, int bufPos)
	{
		bufPos = append(buf, bufPos, dht_segment_head, dht_segment_head_len);
	    bufPos = append(buf, bufPos, ff_mjpeg_bits_dc_luminance, 1, 16);
	    bufPos = append(buf, bufPos, dht_segment_frag, dht_segment_frag_len);
	    bufPos = append(buf, bufPos, ff_mjpeg_val_dc, 12);
	    buf[bufPos++] = 0x10;
	    bufPos = append(buf, bufPos, ff_mjpeg_bits_ac_luminance, 1, 16);
	    bufPos = append(buf, bufPos, ff_mjpeg_val_ac_luminance, 162);
	    buf[bufPos++] = 0x11;
	    bufPos = append(buf, bufPos, ff_mjpeg_bits_ac_chrominance, 1, 16);
	    bufPos = append(buf, bufPos, ff_mjpeg_val_ac_chrominance, 162);
	    return bufPos;
	}

	static boolean check4(byte[] buf, int i, char c0, char c1, char c2, char c3) {
		return buf[i] == (byte)c0 && buf[i + 1] == (byte)c1 && buf[i + 2] == (byte)c2 && buf[i + 3] == (byte)c3;
	}
	
	static byte[] filter(final byte[] buf, int buf_size)
	{
	    int input_skip, output_size;

	    if (buf_size < 12) {
	        System.err.println("Mjpeg2jpeg_bsf: input is truncated");
	        return null;
	    }
	    if (!check4(buf, 6, 'A', 'V', 'I', '1')) {
	    	System.err.println("Mjpeg2jpeg_bsf: input is not MJPEG/AVI1");
	        return null;
	    }
	    input_skip = (((int)buf[4]) << 8) + (int)buf[5] + 4;
	    if (buf_size < input_skip) {
	    	System.err.println("Mjpeg2jpeg_bsf: input is truncated\n");
	        return null;
	    }
	    output_size = buf_size - input_skip + jpeg_header_len + dht_segment_size;
	    byte[] res = new byte[output_size];
	    int outPos = 0;
	    outPos = append(res, outPos, jpeg_header, jpeg_header_len);
	    outPos = append_dht_segment(res, outPos);
	    outPos = append(res, outPos, buf, input_skip, buf_size - input_skip);
	    return res;
	}
	
	static byte[] prependJpegHeader(final byte[] buf, int buf_size)
	{
	    byte[] res = new byte[buf_size - 2 + jpeg_header_len];
	    int outPos = 0;
	    outPos = append(res, outPos, jpeg_header, jpeg_header_len);
	    outPos = append(res, outPos, buf, 2, buf_size - 2);
	    return res;
	}
}
