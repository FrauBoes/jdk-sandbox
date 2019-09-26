/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.Files;

/*
 * @test
 * @summary jpackage create image using main class from main module
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @modules jdk.jpackage
 * @run main/othervm -Xmx512m AppMainClassModuleTest
 */
public class AppMainClassModuleTest {

    private static final String OUTPUT = "output";
    private static final String app = JPackagePath.getApp();
    private static final String appOutput = JPackagePath.getAppOutputFile();

    private static final String[] CMD = {
        "--package-type", "app-image",
        "--input", "input",
        "--dest", OUTPUT,
        "--name", "test",
        "--module", "com.hello",
        "--module-path", "input"
    };

    private static final String[] CMD_MAIN_CLASS = {
        "--package-type", "app-image",
        "--input", "input",
        "--dest", OUTPUT,
        "--name", "test",
        "--module", "com.hello/com.hello.Hello",
        "--module-path", "input"
    };

    private static void validate(String buildOutput) throws Exception {

        File outfile = new File(appOutput);
        int retVal = JPackageHelper.execute(outfile, app);
        if (retVal != 0) {
            throw new AssertionError(
                    "Test application exited with error: ");
        }

        if (!outfile.exists()) {
            throw new AssertionError(appOutput + " was not created");
        }
        String output = Files.readString(outfile.toPath());
        String[] result = output.split("\n");

        if (!result[0].trim().equals("jpackage test application")) {
            throw new AssertionError("Unexpected result[0]: " + result[0]);
        }

        if (!result[1].trim().equals("args.length: 0")) {
            throw new AssertionError("Unexpected result[1]: " + result[1]);
        }
    }

    private static void testMainClassFromModule() throws Exception {
        JPackageHelper.createHelloModule(
                new JPackageHelper.ModuleArgs(null, "com.hello.Hello"));

        validate(JPackageHelper.executeCLI(true, CMD));
        JPackageHelper.deleteOutputFolder(OUTPUT);
        validate(JPackageHelper.executeToolProvider(true, CMD));
        JPackageHelper.deleteOutputFolder(OUTPUT);
    }

    private static void testMainClassFromCLI() throws Exception {
        JPackageHelper.createHelloModule(
                new JPackageHelper.ModuleArgs(null, "com.hello.Hello2"));

        validate(JPackageHelper.executeCLI(true, CMD_MAIN_CLASS));
        JPackageHelper.deleteOutputFolder(OUTPUT);
        validate(JPackageHelper.executeToolProvider(true, CMD_MAIN_CLASS));
    }

    public static void main(String[] args) throws Exception {
        testMainClassFromModule();
        testMainClassFromCLI();
    }

}
