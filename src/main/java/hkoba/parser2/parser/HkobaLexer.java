package hkoba.parser2.parser;

import hkoba.parser2.ITokenType;
import hkoba.parser2.TokenContext;
import hkoba.parser2.TokenData;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HkobaLexer {
    protected AbstractRegisterer register;

    private void commitRegister() {
        if (register != null) {
            register.commit(new ArrayList(register.entryMap.values()));
            register = null;
        }
    }

    abstract class AbstractRegisterer<T extends AbstractEntry> {
        private int count = 0;

        private Map<Integer, T> entryMap = new HashMap<>();

        protected AbstractRegisterer() {
            commitRegister();
            register = this;
        }

        protected <T> void entryChild(Object obj, T... others) {
            if (obj instanceof AbstractEntry) {
                entryMap.remove(((AbstractEntry) obj).index);
            }
            if (others.length > 0) {
                for (Object o : others) {
                    if (o instanceof AbstractEntry) {
                        entryMap.remove(((AbstractEntry) o).index);
                    }
                }
            }
        }

        abstract protected void commit(List<T> entryList);
    }

    abstract class AbstractEntry {
        private int index;

        protected AbstractEntry() {
            index = register.count++;
            register.entryMap.put(index, this);
        }

        /**
         * 子要素として登録する
         *
         * @param obj
         * @param others
         */
        protected <T> void entryChild(Object obj, T... others) {
            register.entryChild(obj, others);
        }
    }

    @FunctionalInterface
    public interface ILexer {
        /**
         * 先頭からの一致チェック
         *
         * @param text
         * @return 先頭から一致した文字数
         */
        int matchSize(String text);
    }

    public class EntryLexer extends AbstractEntry implements ILexer {
        private final ILexer lexer;


        private EntryLexer(ILexer lexer) {
            this.lexer = lexer;
        }

        @Override
        public int matchSize(String text) {
            return lexer.matchSize(text);
        }

        public void value(Function<String, Object> resolver) {
            register(((Token) register).tokenType, this).value(resolver);
            entryChild(this);
        }

        public EntryLexer join(ILexer lexer, ILexer... others) {
            entryChild(lexer, others);
            List<ILexer> lexerList = new ArrayList<>();
            lexerList.add(lexer);
            if (others.length > 0) {
                lexerList.addAll(Arrays.asList(others));
            }
            entryChild(this);
            return new EntryLexer(s -> {
                int ret = matchSize(s);
                if (ret < 0) {
                    return -1;
                }
                String text = s.substring(ret);
                for (ILexer lex : lexerList) {
                    int len = lex.matchSize(text);
                    if (len < 0) {
                        return -1;
                    }
                    ret += len;
                    text = text.substring(len);
                }
                return ret;
            });
        }

        public EntryLexer count(int count) {
            return count(count, count);
        }

        public EntryLexer count(int min, int max) {
            entryChild(this);
            return new EntryLexer(s -> {
                int ret = 0;
                String text = s;
                int count = 0;
                for (int i = 0; i < max; i++) {
                    int len = matchSize(text);
                    if (len < 0) {
                        break;
                    } else if (len == 0) {
                        // これ以降は 0 マッチ
                        count = max;
                        break;
                    }
                    ret += len;
                    text = text.substring(len);
                    count++;
                }
                if (count < min) {
                    return -1;
                }
                return ret;
            });
        }
    }

    public class TokenPattern implements ILexer {
        private final ITokenType type;
        private final List<ILexer> lexerList;
        private Function<String, Object> resolver;

        private TokenPattern(ITokenType type, ILexer lexer, ILexer... others) {
            this.type = type;
            lexerList = new ArrayList<>();
            lexerList.add(lexer);
            if (others.length > 0) {
                lexerList.addAll(Arrays.asList(others));
            }
        }

        @Override
        public int matchSize(String text) {
            int ret = -1;
            for (ILexer lex : lexerList) {
                ret = Math.max(lex.matchSize(text), ret);
            }
            return ret;
        }

        public void value(Function<String, Object> resolver) {
            this.resolver = resolver;
        }
    }

    public abstract class Token extends AbstractRegisterer<EntryLexer> {
        private final ITokenType tokenType;

        protected Token(ITokenType type) {
            tokenType = type;
        }

        protected EntryLexer or(ILexer lexer, ILexer... others) {
            entryChild(lexer, others);
            List<ILexer> lexerList = new ArrayList<>();
            lexerList.add(lexer);
            if (others.length > 0) {
                lexerList.addAll(Arrays.asList(others));
            }
            return new EntryLexer(s -> {
                int ret = -1;
                for (ILexer lex : lexerList) {
                    ret = Math.max(lex.matchSize(s), ret);
                }
                return ret;
            });
        }

        protected EntryLexer _t(String text, String... others) {
            List<String> textList = new ArrayList<>();
            textList.add(text);
            if (others.length > 0) {
                textList.addAll(Arrays.asList(others));
                textList.sort(Comparator.comparingInt(String::length).reversed());
            }
            return new EntryLexer(s -> {
                for (String t : textList) {
                    if (s.startsWith(t)) {
                        return t.length();
                    }
                }
                return -1;
            });
        }

        protected EntryLexer _reg(String regex, String... others) {
            List<Pattern> patternList = new ArrayList<>();
            patternList.add(Pattern.compile(regex));
            if (others.length > 0) {
                for (String reg : others) {
                    patternList.add(Pattern.compile(reg));
                }
            }
            return new EntryLexer(s -> {
                int ret = -1;
                for (Pattern pat : patternList) {
                    Matcher matcher = pat.matcher(s);
                    if (matcher.find()) {
                        String text = matcher.group();
                        if (s.startsWith(text)) {
                            ret = Math.max(ret, text.length());
                        }
                    }
                }
                return ret;
            });
        }

        protected EntryLexer join(ILexer lexer, ILexer... others) {
            entryChild(lexer, others);
            List<ILexer> lexerList = new ArrayList<>();
            lexerList.add(lexer);
            if (others.length > 0) {
                lexerList.addAll(Arrays.asList(others));
            }
            return new EntryLexer(s -> {
                int ret = 0;
                String text = s;
                for (ILexer lex : lexerList) {
                    int len = lex.matchSize(text);
                    if (len < 0) {
                        return -1;
                    }
                    ret += len;
                    text = text.substring(len);
                }
                return ret;
            });
        }

        @Override
        protected void commit(List<EntryLexer> entryList) {
            entryList.forEach(e -> HkobaLexer.this.register(tokenType, e));
        }
    }

    private Map<String, List<TokenPattern>> tokenPatternMap = new HashMap<>();

    public TokenPattern register(ITokenType type, ILexer lexer, ILexer... others) {
        List<TokenPattern> list = tokenPatternMap.get(type.getTokenName());
        if (list == null) {
            list = new ArrayList<>();
            tokenPatternMap.put(type.getTokenName(), list);
        }
        TokenPattern ret = new TokenPattern(type, lexer, others);
        list.add(ret);
        return ret;
    }

    private class LexerStream {
        private class LexerContext extends TokenContext {
            @Getter
            private final TokenContext.Index index;

            @Getter
            private final String whiteSpace;

            private List<TokenData.TextToken> tokenList = new ArrayList<>();

            /**
             * 登録済みトークンマップ
             * key: tokenType
             */
            private Map<String, TokenData.TextToken> tokenMap = new HashMap<>();

            /**
             * テキストマップ
             * key: text
             */
            private Map<String, TokenData.TextToken> textMap = new HashMap<>();

            @Override
            public boolean isEof() {
                return this.index.getIndex() >= text.length();
            }

            private LexerContext(String whiteSpace) {
                this.whiteSpace = whiteSpace;
                this.index = LexerStream.this.getIndex();
                // トークンの作成
                String targetText = text.substring(this.index.getIndex());
                tokenPatternMap.forEach((k, p) -> {
                    int ret = -1;
                    Index end = null;
                    TokenPattern pattern = null;
                    for (TokenPattern pat : p) {
                        int len = pat.matchSize(targetText);
                        if (len > ret) {
                            ret = len;
                            pattern = pat;
                            end = moveIndex(this.index.getIndex() + len).getIndex();
                            moveIndex(this.index);
                        }
                    }
                    if (ret >= 0) {
                        Object value;
                        String token = targetText.substring(0, ret);
                        if (pattern.resolver != null) {
                            value = pattern.resolver.apply(token);
                        } else {
                            value = null;
                        }
                        tokenList.add(new TokenData.TextToken(pattern.type, this, end, token, value));
                    }
                });
                // キャッシュ作成
                tokenList.forEach(t -> {
                    String tp = t.getType().getTokenName();
                    if (!tokenMap.containsKey(tp) || t.getText().length() > tokenMap.get(tp).getText().length()) {
                        tokenMap.put(tp, t);
                    }
                    if (!textMap.containsKey(t.getText())) {
                        textMap.put(t.getText(), t);
                    }
                });
            }

            @Override
            public Optional<TokenData.TextToken> getToken(String text) {
                if (!textMap.containsKey(text)) {
                    // まだチェックをしていない
                    // 無名の可能性あり
                    moveIndex(this.index);
                    if (LexerStream.this.text.substring(this.index.getIndex()).startsWith(text)) {
                        Index end = moveIndex(this.index.getIndex() + text.length()).getIndex();
                        // 無名クラスは値は null
                        textMap.put(text, new TokenData.TextToken(ITokenType.CToken.UNKNOWN, this, end, text, null));
                    } else {
                        // チェック済みとして null を設定
                        textMap.put(text, null);
                    }
                }
                return Optional.ofNullable(textMap.get(text));
            }

            @Override
            public Optional<TokenData> getToken(ITokenType type) {
                return Optional.ofNullable(tokenMap.get(type.getTokenName()));
            }

            @Override
            public TokenContext seek(int index) {
                return moveIndex(index).getContext();
            }

            @Override
            public TokenContext restore(Index index) {
                return moveIndex(index).getContext();
            }

            @Override
            public List<TokenData> getTokens() {
                return (List) tokenList;
            }
        }

        private int index;
        private int row;
        private int col;

        private String text;

        private Map<Integer, LexerContext> contextMap = new HashMap<>();

        private LexerStream(String text) {
            this.text = text;
            index = 0;
            row = col = 1;
        }

        private TokenContext.Index getIndex() {
            return new TokenContext.Index(index, row, col);
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
            moveIndex(ix);
            return result;
        }

        private LexerStream moveIndex(int ix) {
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

        private LexerStream moveIndex(TokenContext.Index ix) {
            index = ix.getIndex();
            row = ix.getRow();
            col = ix.getCol();
            return this;
        }

        private LexerContext getContext() {
            String space = skipSpace();
            LexerContext data = contextMap.get(index);
            if (data == null) {
                data = new LexerContext(space);
                contextMap.put(index, data);
            }
            return data;
        }
    }

    @Getter
    @Setter
    private String whiteSpaces = " \t\r\n";

    public TokenContext getLexerContext(String text) {
        commitRegister();
        return new LexerStream(text).getContext();
    }

    public TokenContext getContext(String text) {
        return getLexerContext(text);
    }
}
