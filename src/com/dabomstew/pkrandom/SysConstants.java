package com.dabomstew.pkrandom;

/*----------------------------------------------------------------------------*/
/*--  SysConstants.java - contains constants not related to the             --*/
/*--                      randomization process itself, such as those       --*/
/*--                      relating to file I/O and the updating system.     --*/
/*--                                                                        --*/
/*--  Part of "Universal Pokemon Randomizer ZX" by the UPR-ZX team          --*/
/*--  Originally part of "Universal Pokemon Randomizer" by Dabomstew        --*/
/*--  Pokemon and any associated names and the like are                     --*/
/*--  trademark and (C) Nintendo 1996-2020.                                 --*/
/*--                                                                        --*/
/*--  The custom code written here is licensed under the terms of the GPL:  --*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/
/*  Modified 2026-11-28 for "UPR ZX for Android (unofficial)": ROOT_PATH comes from system property uprzx.rootPath and fails loudly if unset (BUILD_SPEC 4.2).  */

import java.io.File;

public class SysConstants {

    public static final String AUTOUPDATE_URL = "http://pokehacks.dabomstew.com/randomizer/autoupdate/";
    public static final String WEBSITE_URL = "http://pokehacks.dabomstew.com/randomizer/";
    public static final String WEBSITE_URL_ZX = "https://github.com/Ajarmar/universal-pokemon-randomizer-zx/releases";
    public static final String WIKI_URL_ZX = "https://github.com/Ajarmar/universal-pokemon-randomizer-zx/wiki";
    public static final String API_URL_ZX = "https://api.github.com/repos/ajarmar/universal-pokemon-randomizer-zx/releases/latest";
    public static final int UPDATE_VERSION = 1721;
    public static final String ROOT_PATH = getRootPath();
    public static final String LINE_SEP = System.getProperty("line.separator");
    public static final String customNamesFile = "customnames.rncn";

    // OLD custom names files
    public static final String tnamesFile = "trainernames.txt";
    public static final String tclassesFile = "trainerclasses.txt";
    public static final String nnamesFile = "nicknames.txt";

    /** System property the host must set, before anything reads SysConstants, to the working root folder. */
    public static final String ROOT_PATH_PROPERTY = "uprzx.rootPath";

    private static String getRootPath() {
        // Android port: the root is set by the host (app-private storage), not derived from the JAR location.
        // There is deliberately no fallback: an unwritable "./" made NDSRom/NCCH keep extracted files in RAM.
        String root = System.getProperty(ROOT_PATH_PROPERTY);
        if (root == null || root.isEmpty()) {
            throw new IllegalStateException("SysConstants.ROOT_PATH is not configured: set system property "
                    + ROOT_PATH_PROPERTY + " to a writable folder before using the engine");
        }
        File fh = new File(root);
        if (!fh.isDirectory() || !fh.canWrite()) {
            throw new IllegalStateException("SysConstants.ROOT_PATH is not a writable folder: " + root);
        }
        return fh.getAbsolutePath() + File.separator;
    }

}
