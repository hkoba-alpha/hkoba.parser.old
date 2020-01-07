package hkoba.parser.analyze;

import hkoba.parser.TokenData;
import hkoba.parser.TokenMap;
import lombok.Getter;
import lombok.Setter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LexicalAnalyzer {

    private class ParserContext {
        private class LexicalTokenMap extends TokenMap {
            @Getter
            private final TokenData.Index index;

            @Getter
            private final String whiteSpace;

            private List<TokenData.TextToken<?>> tokenList;

            private Map<TokenData.TokenType, TokenData.TextToken<?>> cacheMap = new HashMap<>();

            private Map<String, TokenData.TextToken<?>> textTokenMap = new HashMap<>();

            private LexicalTokenMap(String whiteSpace) {
                this.index = ParserContext.this.getIndex();
                this.whiteSpace = whiteSpace;
                String target = ParserContext.this.text.substring(this.index.getIndex());
                parserEntryMap.forEach((k, v) -> {
                    Function<String, Object> resolver = null;
                    String token = "";
                    TokenData.Index end = null;
                    for (ParserEntryData entry : v) {
                        for (Function<String, String> parser : entry.parsers) {
                            String result = parser.apply(target);
                            if (result != null && result.length() > token.length()) {
                                token = result;
                                resolver = entry.resolver;
                                ParserContext.this.moveNext(this.index.getIndex() + token.length());
                                end = ParserContext.this.getIndex();
                                restore(this.index);
                            }
                        }
                    }
                    if (token.length() > 0) {
                        // ヒットした
                        Object value = (resolver != null ? resolver.apply(token) : token);
                        cacheMap.put(k, new TokenData.TextToken(k, this, end, token, value));
                    }
                });
                // エイリアス
                aliasMap.forEach((k, v) -> {
                    if (cacheMap.containsKey(k)) {
                        return;
                    }
                    TokenData.TextToken data = null;
                    for (TokenData.TokenType tp : v) {
                        TokenData.TextToken dt = cacheMap.get(tp);
                        if (dt != null && (data == null || dt.getEnd().getIndex() > data.getEnd().getIndex())) {
                            // 更新
                            data = dt;
                        }
                    }
                    if (data != null) {
                        // Aliasできた
                        cacheMap.put(k, new TokenData.TextToken<>(k, this, data.getEnd(), data.getText(), data.getValue()));
                    }
                });
            }

            @Override
            public boolean isEof() {
                return this.index.getIndex() >= ParserContext.this.text.length();
            }

            @Override
            public TokenMap next(int index) {
                return ParserContext.this.moveNext(index).getTokenMap();
            }

            @Override
            public TokenMap move(TokenData.Index index) {
                return ParserContext.this.restore(index).getTokenMap();
            }


            @Override
            public <T> Optional<TokenData.TextToken<T>> getTextToken(String text) {
                if (!textTokenMap.containsKey(text)) {
                    move(index);
                    if (ParserContext.this.text.indexOf(text, index.getIndex()) == index.getIndex()) {
                        ParserContext.this.moveNext(index.getIndex() + text.length());
                        TokenData.Index end = ParserContext.this.getIndex();
                        textTokenMap.put(text, new TokenData.TextToken(TokenData.UNKNOWN, this, end, text, text));
                    } else {
                        textTokenMap.put(text, null);
                    }
                }
                return Optional.ofNullable((TokenData.TextToken<T>) textTokenMap.get(text));
            }

            @Override
            public <T, U extends TokenData<T>> Optional<U> getToken(TokenData.TokenType type) {
                return Optional.ofNullable((U) cacheMap.get(type));
            }

            @Override
            public List<? extends TokenData> getTokens() {
                if (tokenList == null) {
                    this.tokenList = new ArrayList<>(cacheMap.values());
                }
                return tokenList;
            }
        }


        private StringBuilder text;
        private int index;
        private int row;
        private int col;

        private Map<Integer, LexicalTokenMap> tokenMap = new HashMap<>();

        private ParserContext(StringBuilder text) {
            this.text = text;
            index = 0;
            row = col = 1;
        }

        private ParserContext moveNext(int ix) {
            if (ix > index) {
                int ex = text.lastIndexOf("\n", ix - 1);
                if (ex >= index) {
                    // 改行あり
                    row += (ix - index) - text.substring(index, ix).replaceAll("\\n", "").length();
                    col = ix - ex;
                } else {
                    // 改行なし
                    col += (ix - index);
                }
                index = ix;
            }
            return this;
        }

        private TokenData.Index getIndex() {
            return new TokenData.Index(index, row, col);
        }

        private ParserContext restore(TokenData.Index ix) {
            index = ix.getIndex();
            row = ix.getRow();
            col = ix.getCol();
            return this;
        }

        private String skipSpace() {
            int ix = index;
            while (ix < text.length()) {
                if (whiteSpaces.indexOf(text.charAt(ix)) < 0) {
                    break;
                }
                ix++;
            }
            String result = text.substring(index, ix);
            moveNext(ix);
            return result;
        }

        private LexicalTokenMap getTokenMap() {
            String space = skipSpace();
            LexicalTokenMap map = tokenMap.get(index);
            if (map == null) {
                map = new LexicalTokenMap(space);
                tokenMap.put(index, map);
            }
            return map;
        }
    }

    @Getter
    @Setter
    private String whiteSpaces = " \t\r\n";

    private static class ParserEntryData {
        private List<Function<String, String>> parsers = new ArrayList<>();
        private Function<String, Object> resolver;
    }

    private Map<TokenData.TokenType, List<ParserEntryData>> parserEntryMap = new HashMap<>();

    /**
     * 別トークンへのエイリアス
     */
    private Map<TokenData.TokenType, List<TokenData.TokenType>> aliasMap = new HashMap<>();

    public class ParserEntry {
        private final TokenData.TokenType type;
        private ParserEntryData entryData;
        private List<ParserEntryData> entryList;

        private ParserEntry(TokenData.TokenType type) {
            this.type = type;
            entryList = parserEntryMap.get(type);
        }

        /**
         * 別の定義をエイリアスとして取り込む
         *
         * @param type
         * @param others
         * @return
         */
        public ParserEntry alias(CharSequence type, CharSequence... others) {
            List<TokenData.TokenType> list = aliasMap.get(this.type);
            if (list == null) {
                list = new ArrayList<>();
                aliasMap.put(this.type, list);
            }
            if (type instanceof TokenData.TokenType) {
                list.add((TokenData.TokenType) type);
            } else {
                list.add(TokenData.token(type.toString()));
            }
            for (CharSequence tp : others) {
                if (tp instanceof TokenData.TokenType) {
                    list.add((TokenData.TokenType) tp);
                } else {
                    list.add(TokenData.token(tp.toString()));
                }
            }
            return this;
        }

        public ParserEntry pattern(Function<String, String> parser, Function<String, String>... others) {
            if (entryList == null) {
                entryList = new ArrayList<>();
                parserEntryMap.put(type, entryList);
            }
            if (entryData == null) {
                entryData = new ParserEntryData();
                entryList.add(entryData);
            }
            entryData.parsers.add(parser);
            for (Function<String, String> p : others) {
                entryData.parsers.add(p);
            }
            return this;
        }

        public ParserEntry value(Function<String, Object> resolver) {
            if (entryData == null) {
                throw new IllegalArgumentException("No Patterns");
            }
            entryData.resolver = resolver;
            entryData = null;
            return this;
        }

        public ParserEntry entry(String type) {
            return LexicalAnalyzer.this.entry(type);
        }

        public ParserEntry entry(TokenData.TokenType type) {
            return LexicalAnalyzer.this.entry(type);
        }
    }

    public ParserEntry entry(String type) {
        return entry(TokenData.token(type));
    }

    public ParserEntry entry(TokenData.TokenType type) {
        return new ParserEntry(type);
    }

    public TokenMap getTokens(String text) {
        return new ParserContext(new StringBuilder(text)).getTokenMap();
    }

    public TokenMap getTokens(Reader reader) throws IOException {
        StringBuilder text = new StringBuilder();
        char[] buf = new char[1024];
        int size;
        while ((size = reader.read(buf)) > 0) {
            text.append(buf, 0, size);
        }
        return new ParserContext(text).getTokenMap();
    }

    /**
     * 決められた文字列で始まる場合に一致する
     *
     * @param word
     * @return
     */
    public static Function<String, String> word(String word, String... others) {
        return s -> {
            int len = 0;
            if (s.startsWith(word)) {
                len = word.length();
            }
            for (String wd : others) {
                if (wd.length() > len && s.startsWith(wd)) {
                    len = wd.length();
                }
            }
            return len > 0 ? s.substring(0, len) : null;
        };
    }

    /**
     * 正規表現として一致するものを探す
     *
     * @param regex
     * @param others
     * @return
     */
    public static Function<String, String> regex(String regex, String... others) {
        List<Pattern> patterns = new ArrayList<>();
        patterns.add(Pattern.compile(regex));
        for (String pat : others) {
            patterns.add(Pattern.compile(pat));
        }
        return s -> {
            int len = 0;
            for (Pattern pat : patterns) {
                Matcher matcher = pat.matcher(s);
                if (matcher.find()) {
                    String res = matcher.group();
                    if (res.length() > len && s.startsWith(res)) {
                        len = res.length();
                    }
                }
            }
            return len > 0 ? s.substring(0, len) : null;
        };
    }

    /**
     * 指定した文字数だけ抽出する
     *
     * @param size
     * @return
     */
    public static Function<String, String> left(int size) {
        return s -> s.length() >= size ? s.substring(0, size) : null;
    }
}
