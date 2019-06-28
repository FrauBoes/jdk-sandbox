/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#ifndef HEADLESS

#include <stdlib.h>
#include <string.h>

#include "sun_java2d_SunGraphics2D.h"

#include "MTLPaints.h"
#include "MTLVertexCache.h"
#include "common.h"

typedef struct _J2DVertex {
    float position[3];
    float txtpos[2];
} J2DVertex;

static J2DVertex *vertexCache = NULL;
static jint vertexCacheIndex = 0;

static jint maskCacheTexID = 0;
static jint maskCacheIndex = 0;

id<MTLRenderCommandEncoder> encoder;
id<MTLTexture> texturePool[MTLVC_MAX_TEX_INDEX];
static jint texturePoolIndex = 0;

#define MTLVC_ADD_VERTEX(TX, TY, DX, DY, DZ) \
    do { \
        J2DVertex *v = &vertexCache[vertexCacheIndex++]; \
        v->txtpos[0] = TX; \
        v->txtpos[1] = TY; \
        v->position[0]= DX; \
        v->position[1] = DY; \
        v->position[2] = DZ; \
    } while (0)

#define MTLVC_ADD_TRIANGLES(DX1, DY1, DX2, DY2) \
    do { \
        MTLVC_ADD_VERTEX(0, 0, DX1, DY1, 0); \
        MTLVC_ADD_VERTEX(1, 0, DX2, DY1, 0); \
        MTLVC_ADD_VERTEX(1, 1, DX2, DY2, 0); \
        MTLVC_ADD_VERTEX(1, 1, DX2, DY2, 0); \
        MTLVC_ADD_VERTEX(0, 1, DX1, DY2, 0); \
        MTLVC_ADD_VERTEX(0, 0, DX1, DY1, 0); \
    } while (0)

jboolean
MTLVertexCache_InitVertexCache()
{
    J2dTraceLn(J2D_TRACE_INFO, "MTLVertexCache_InitVertexCache");

    if (vertexCache == NULL) {
        vertexCache = (J2DVertex *)malloc(MTLVC_MAX_INDEX * sizeof(J2DVertex));
        if (vertexCache == NULL) {
            return JNI_FALSE;
        }
    }

    return JNI_TRUE;
}

void
MTLVertexCache_FlushVertexCache(MTLContext *mtlc)
{
    J2dTraceLn(J2D_TRACE_INFO, "MTLVertexCache_FlushVertexCache");

    if (vertexCacheIndex > 0 ||
        texturePoolIndex > 0) {
        id<MTLBuffer>vertexBuffer = [mtlc.device newBufferWithBytes:vertexCache
                                                 length:vertexCacheIndex * sizeof(J2DVertex)
                                                 options:MTLResourceOptionCPUCacheModeDefault];
        [encoder setVertexBuffer:vertexBuffer offset:0 atIndex:MeshVertexBuffer];
        for (int i = 0; i < texturePoolIndex; i++) {
            J2dTraceLn1(J2D_TRACE_INFO, "MTLVertexCache_FlushVertexCache : draw texture at index %d", i);
            [encoder setFragmentTexture:texturePool[i] atIndex: 0];
            [encoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:i*6 vertexCount:6];
        }
        [encoder endEncoding];
    }
    vertexCacheIndex = 0;
    texturePoolIndex = 0;
}

/**
 * This method is somewhat hacky, but necessary for the foreseeable future.
 * The problem is the way OpenGL handles color values in vertex arrays.  When
 * a vertex in a vertex array contains a color, and then the vertex array
 * is rendered via glDrawArrays(), the global OpenGL color state is actually
 * modified each time a vertex is rendered.  This means that after all
 * vertices have been flushed, the global OpenGL color state will be set to
 * the color of the most recently rendered element in the vertex array.
 *
 * The reason this is a problem for us is that we do not want to flush the
 * vertex array (in the case of mask/glyph operations) or issue a glEnd()
 * (in the case of non-antialiased primitives) everytime the current color
 * changes, which would defeat any benefit from batching in the first place.
 * We handle this in practice by not calling CHECK/RESET_PREVIOUS_OP() when
 * the simple color state is changing in MTLPaints_SetColor().  This is
 * problematic for vertex caching because we may end up with the following
 * situation, for example:
 *   SET_COLOR (orange)
 *   MASK_FILL
 *   MASK_FILL
 *   SET_COLOR (blue; remember, this won't cause a flush)
 *   FILL_RECT (this will cause the vertex array to be flushed)
 *
 * In this case, we would actually end up rendering an orange FILL_RECT,
 * not a blue one as intended, because flushing the vertex cache flush would
 * override the color state from the most recent SET_COLOR call.
 *
 * Long story short, the easiest way to resolve this problem is to call
 * this method just after disabling the mask/glyph cache, which will ensure
 * that the appropriate color state is restored.
 */
void
MTLVertexCache_RestoreColorState(MTLContext *mtlc)
{
    // TODO
    if (mtlc.paintState == sun_java2d_SunGraphics2D_PAINT_ALPHACOLOR) {
        [mtlc setColor:mtlc.pixel];
    }
}

static jboolean
MTLVertexCache_InitMaskCache()
{
    // TODO
    J2dTraceLn(J2D_TRACE_INFO, "MTLVertexCache_InitMaskCache");
    return JNI_TRUE;
}

void
MTLVertexCache_EnableMaskCache(MTLContext *mtlc)
{
    // TODO
    J2dTraceLn(J2D_TRACE_INFO, "MTLVertexCache_EnableMaskCache");
}

void
MTLVertexCache_DisableMaskCache(MTLContext *mtlc)
{
    // TODO
    J2dTraceLn(J2D_TRACE_INFO, "MTLVertexCache_DisableMaskCache");
    maskCacheIndex = 0;
}

void
MTLVertexCache_AddVertexTriangles(jfloat dx1, jfloat dy1,
                                  jfloat dx2, jfloat dy2)
{
    J2dTraceLn(J2D_TRACE_INFO, "MTLVertexCache_AddVertexTriangles");
    MTLVC_ADD_TRIANGLES(dx1, dy1, dx2, dy2);
}

void
MTLVertexCache_AddGlyphTexture(MTLContext *mtlc,
                               jint width, jint height,
                               GlyphInfo *ginfo,
                               BMTLSDOps *dstOps)
{
    J2dTraceLn(J2D_TRACE_INFO, "MTLVertexCache_AddGlyphTexture");
    J2dTraceLn2(J2D_TRACE_INFO, "Glyph width = %d Glyph height = %d", width, height);
    if (texturePoolIndex >= MTLVC_MAX_TEX_INDEX ||
        vertexCacheIndex >= MTLVC_MAX_INDEX)
    {
        MTLVertexCache_FlushVertexCache(mtlc);
        MTLVertexCache_CreateSamplingEncoder(mtlc, dstOps);
    }
    MTLTextureDescriptor *textureDescriptor = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatA8Unorm width:width height:height mipmapped:NO];
    id <MTLTexture> texture = [mtlc.device newTextureWithDescriptor:textureDescriptor];
    J2dTraceLn3(J2D_TRACE_INFO, "MTLVertexCache_AddGlyphTexture: created texture: tex=%p, w=%d h=%d", texture, width, height);
    NSUInteger bytesPerRow = 1 * width;

    MTLRegion region = {
        { 0, 0, 0 },
        {width, height, 1}
    };
    [texture replaceRegion:region
             mipmapLevel:0
             withBytes:ginfo->image
             bytesPerRow:bytesPerRow];
    texturePool[texturePoolIndex] = texture;
    texturePoolIndex++;
}

void
MTLVertexCache_CreateSamplingEncoder(MTLContext *mtlc, BMTLSDOps *dstOps) {
    J2dTraceLn(J2D_TRACE_INFO, "MTLVertexCache_CreateSamplingEncoder");
    encoder = [mtlc createSamplingEncoderForDest:dstOps->pTexture];
}

#endif /* !HEADLESS */
