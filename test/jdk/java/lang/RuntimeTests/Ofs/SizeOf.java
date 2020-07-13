/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @summary Test for Runtime.sizeOf
 * @library /test/lib
 *
 * @run main/othervm -Xmx128m -Xint                   SizeOf
 * @run main/othervm -Xmx128m -XX:TieredStopAtLevel=1 SizeOf
 * @run main/othervm -Xmx128m -XX:TieredStopAtLevel=2 SizeOf
 * @run main/othervm -Xmx128m -XX:TieredStopAtLevel=3 SizeOf
 * @run main/othervm -Xmx128m -XX:TieredStopAtLevel=4 SizeOf
 * @run main/othervm -Xmx128m -XX:-TieredCompilation  SizeOf
 */

import jdk.test.lib.Platform;

public class SizeOf {

    public static void main(String ... args) {
        testSize_newObject();
        testSize_localObject();
        testSize_fieldObject();
        testNulls();
    }

    private static void testSize_newObject() {
        int expected = Platform.is64bit() ? 16 : 8;
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertEquals(expected, Runtime.sizeOf(new Object()));
        }
    }

    private static void testSize_localObject() {
        int expected = Platform.is64bit() ? 16 : 8;
        Object o = new Object();
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertEquals(expected, Runtime.sizeOf(o));
        }
    }

    static Object staticO = new Object();

    private static void testSize_fieldObject() {
        int expected = Platform.is64bit() ? 16 : 8;
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertEquals(expected, Runtime.sizeOf(staticO));
        }
    }

    private static void testNulls() {
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            try {
                Runtime.sizeOf(null);
                RuntimeOfUtil.assertFail();
            } catch (NullPointerException e) {
                // expected
            }
        }
    }

}
