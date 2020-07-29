/*
 * Copyright (c) 2020, SAP. All rights reserved.
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * This is a stress test for allocating from a single Arena (SpaceManager) from
 *  multiple threads, optionally with reserve limit (mimicking the non-expandable CompressedClassSpace)
 * or commit limit (mimimcking MaxMetaspaceSize).
 *
 * The test threads will start to allocate from the Arena, and occasionally deallocate.
 * The threads run with a safety allocation max; if reached (or, if the underlying arena
 * hits either commit or reserve limit, if given) they will switch to deallocation and then
 * kind of float at the allocation ceiling, alternating between allocation and deallocation.
 *
 * We test with various flags, to exercise all 3 reclaim policies (none, balanced (default)
 * and aggessive) as well as one run with allocation guards enabled.
 *
 * We also set MetaspaceVerifyInterval very low to trigger many verifications in debug vm.
 *
 */


/*
 * @test id=debug
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build sun.hotspot.WhiteBox
 * @key randomness stress
 * @requires (vm.debug == true)
 *
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *
 * @run main/othervm/timeout=400 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI  -XX:VerifyMetaspaceInterval=10                                        TestMetaspaceAllocationMT1
 * @run main/othervm/timeout=400 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI  -XX:VerifyMetaspaceInterval=10  -XX:MetaspaceReclaimPolicy=none       TestMetaspaceAllocationMT1
 * @run main/othervm/timeout=400 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI  -XX:VerifyMetaspaceInterval=10  -XX:MetaspaceReclaimPolicy=aggressive TestMetaspaceAllocationMT1
 * @run main/othervm/timeout=400 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI  -XX:VerifyMetaspaceInterval=10  -XX:+MetaspaceGuardAllocations        TestMetaspaceAllocationMT1
 *
 */

/*
 * @test id=ndebug
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build sun.hotspot.WhiteBox
 * @key randomness stress
 * @requires (vm.debug == false)
 *
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *
 * @run main/othervm/timeout=400 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI                                        TestMetaspaceAllocationMT1
 * @run main/othervm/timeout=400 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI  -XX:MetaspaceReclaimPolicy=none       TestMetaspaceAllocationMT1
 * @run main/othervm/timeout=400 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI  -XX:MetaspaceReclaimPolicy=aggressive TestMetaspaceAllocationMT1
 */

public class TestMetaspaceAllocationMT1 {

    public static void main(String[] args) throws Exception {

        final long testAllocationCeiling = 1024 * 1024 * 8; // 8m words = 64M on 64bit
        final int numThreads = 4;
        final int seconds = 8;

        for (int i = 0; i < 3; i ++) {

            long commitLimit = (i == 1) ? testAllocationCeiling / 2 : 0;
            long reserveLimit = (i == 2) ? testAllocationCeiling / 2 : 0;

            System.out.println("#### Test: ");
            System.out.println("#### testAllocationCeiling: " + testAllocationCeiling);
            System.out.println("#### numThreads: " + numThreads);
            System.out.println("#### seconds: " + seconds);
            System.out.println("#### commitLimit: " + commitLimit);
            System.out.println("#### reserveLimit: " + reserveLimit);
            System.out.println("#### ReclaimPolicy: " + Settings.settings().reclaimPolicy);
            System.out.println("#### guards: " + Settings.settings().usesAllocationGuards);

            MetaspaceTestContext context = new MetaspaceTestContext(commitLimit, reserveLimit);
            MetaspaceTestOneArenaManyThreads test = new MetaspaceTestOneArenaManyThreads(context, testAllocationCeiling, numThreads, seconds);

            try {
                test.runTest();
            } catch (RuntimeException e) {
                System.out.println(e);
                context.printToTTY();
                throw e;
            }

            context.destroy();

            System.out.println("#### Done. ####");
            System.out.println("###############");

        }

    }

}