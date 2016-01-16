#include <stdio.h>

#include "vpx/vpx_encoder.h"
#include "vpx/vp8cx.h"
#include "vpx/vpx_decoder.h"
#define interface (&vpx_codec_vp8_cx_algo)

// include vpxenc example source directly because it has lots of useful functions
#include "vpxenc.c"

int test_vpx() {
	vpx_codec_enc_cfg_t  cfg;
	FILE *outfile;
	vpx_image_t vpx_image;
	vpx_codec_ctx_t codec;
	EbmlGlobal ebml = {0};
	struct vpx_rational arg_framerate = {30, 1};
	uint32_t hash = 0;
	
	if (0!=vpx_codec_enc_config_default(interface, &cfg, 0)) return 1000;
	cfg.g_w = 480;
	cfg.g_h = 320;
	
	outfile = fopen("/mnt/sdcard/1.webm", "wb");
	if (!outfile) return 1000;
	
	if (!vpx_img_alloc(&vpx_image, VPX_IMG_FMT_YV12, cfg.g_w, cfg.g_h, 1)) return 1001;
	
	if (0!=vpx_codec_enc_init(&codec, interface, &cfg, 0)) return 1002;
	
	
    ebml.stream = outfile;
    write_webm_file_header(&ebml, &cfg, &arg_framerate);
	
	int numFrames = 127;
	int frameCounter = 0;
	while (frameCounter <= numFrames) {
		if (frameCounter < numFrames) {
			unsigned char *yplane = vpx_image.planes[0];
			int index = 0;
			int x,y;
			for(y=0; y<cfg.g_h/2; y++) {
				for(x=0; x<cfg.g_w; x++) {
					unsigned char brightness = 2 * frameCounter;
					yplane[index] = brightness;
					index++;
				}
			}
		}
		
		vpx_codec_err_t result = vpx_codec_encode(&codec, (frameCounter<numFrames) ? &vpx_image : NULL, frameCounter, 1, 0, VPX_DL_REALTIME);
		if (result==VPX_CODEC_INCAPABLE) {
			fclose(outfile);
			return 2100 + frameCounter;
		}
		if (result==VPX_CODEC_INVALID_PARAM) {
			fclose(outfile);
			return 2200 + frameCounter;
		}
		if (result!=VPX_CODEC_OK) {
			fclose(outfile);
			return 2300 + frameCounter;
		}
		
        vpx_codec_iter_t iter = NULL;
        const vpx_codec_cx_pkt_t *pkt;
		while( (pkt = vpx_codec_get_cx_data(&codec, &iter)) ) {
			switch (pkt->kind) {
				case VPX_CODEC_CX_FRAME_PKT:
					hash = murmur(pkt->data.frame.buf, pkt->data.frame.sz, hash);
					write_webm_block(&ebml, &cfg, pkt);
					break;
			}
		}
		
		frameCounter++;
	}
	
	
	write_webm_file_footer(&ebml, hash);
	
	if (0!=vpx_codec_destroy(&codec)) return 1099;
	fclose(outfile);

	return 11;	
}

#include <jni.h>
jint Java_com_dozingcatsoftware_eyeball_EyeballMain_testVPX(JNIEnv* env, jobject thiz) {
	return test_vpx();
}

