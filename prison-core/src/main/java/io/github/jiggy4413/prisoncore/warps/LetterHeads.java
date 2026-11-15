package io.github.jiggy4413.prisoncore.warps;

import java.util.HashMap;
import java.util.Map;

/**
 * Built-in letter heads for lettered mines, one material per tier so the menu climbs in value:
 * A-E cobblestone, F-J quartz, K-O gold, P-T emerald, U-Y diamond, Z netherite.
 * Values are textures.minecraft.net texture ids of public alphabet heads (Minecraft-Heads).
 */
final class LetterHeads {

    private static final Map<Character, String> TEXTURES = new HashMap<Character, String>();

    static {
        put('a', "ced0edb0a641d229c817b33fa661712bb26ca526a23fd32bdabfcc9909ee4b1"); // cobblestone
        put('b', "372c21bf23c27fc94d7888f0a557a3a265f0d954a991acff9908ffd568f3a64"); // cobblestone
        put('c', "af8e6151a540394a7327c95d2a266bddc7564cfe2f8ee60f344c9581042dd27"); // cobblestone
        put('d', "3173cbace887a31279f47b2c4b52367f4b2e6dc9872aa6733b3b24272f56c"); // cobblestone
        put('e', "76f081857a7c76dd1c35286468a63ca814a59335c7d923559ea7d7e9c01f1581"); // cobblestone
        put('f', "76ef83e6cab19cd23807ef206d1ca66e41baa3a0fa5bdc9ea44bbe96d186b"); // quartz
        put('g', "c68dfbc95adb4f648c36164a15d68ef5f9c7697d86f87213c7d7a645577aa66"); // quartz
        put('h', "a8d0c67baa725a1e41bab30805777e732bb2ae6e934f8735cc407875d64ccb"); // quartz
        put('i', "9769e2c134ee5fc6daefe412e4af3d57df9dbb3caacd8e36e599979ec1ac4"); // quartz
        put('j', "9c342719a038268e36953aaaeb73eda82de681b23a47891a3ffe7fbe540a312"); // quartz
        put('k', "b8824dcf4ba317533bf29dca18ce7c4fd328b42680e6c322f5f4ff1a394a887"); // gold
        put('l', "597463d7181e83a143a6ced1a1f77f66d1f28e6f272fe8cd95e7fb89ea0dc69"); // gold
        put('m', "a6b8b5eba44993697cb530c2fdb22ce4459bd5c9b36f6899fc1262c0f6ba30"); // gold
        put('n', "e4d29b1332afc4b1eb319a2df2eb9e2eb302f433cdff64095702af7e09b13f7"); // gold
        put('o', "52faf892cb57fa92c53baf57b8b6c2984c4f4c70aefeb6959ff2fadc6623f72"); // gold
        put('p', "91a6d631513ebdcc4c35b803dec1aaf4301a248d3e3ee3fefdff63326cc2f7ec"); // emerald
        put('q', "9661ec6a48579302d56229cd685f30d1ea68576904f7ef43ca53cffa5720ba4"); // emerald
        put('r', "62545fbca1459d89d5d61491cfcc0a9bee5fbcc3c6b9903a51e34aa88eae8850"); // emerald
        put('s', "fe2cdd4bf1f5515b48af91427e74191d40452e17c4b2d45b7803528e001196fe"); // emerald
        put('t', "aad9d46d9fe288d427b704e014337eaf68571c1c048ae8f57858d80dc29a93e7"); // emerald
        put('u', "e184ee71425d6b74c3e6872c3a1177dcbfb0fe799d2c47210b5a44672ce1fba"); // diamond
        put('v', "85727a84297c3342de233becfde73526d8d5605fda2529d2e1514938950e6a8"); // diamond
        put('w', "9796b24c5fd7f5c58bc261a52a2eaf9674ee52a52468fc79fbe29c84447908c"); // diamond
        put('x', "fa4e1bb4eb76b712399592cb202f613b5b96283f22e41f4b959db7134ff72fa6"); // diamond
        put('y', "516741a6597b683ccebe211f2f41f54d7f2254f58459adffea7d35c9da21b7"); // diamond
        put('z', "145a24b54b7cca3b9f99b337ea0d1129bc61ff6aa8df17780686d8cc48f55f24"); // netherite
    }

    private LetterHeads() {
    }

    private static void put(char letter, String texture) {
        TEXTURES.put(letter, texture);
    }

    /** The texture id for a letter a-z, or null. */
    static String texture(char letter) {
        return TEXTURES.get(Character.toLowerCase(letter));
    }
}
