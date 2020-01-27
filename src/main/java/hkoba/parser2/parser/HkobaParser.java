package hkoba.parser2.parser;

import hkoba.parser2.ITokenType;
import hkoba.parser2.TokenContext;
import hkoba.parser2.TokenData;
import lombok.ToString;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HkobaParser extends HkobaLexer {
    /**
     * トークンパターン
     */
    @FunctionalInterface
    public interface IParser {
        /**
         * マッチしたトークンを返す
         *
         * @param context  コンテキスト
         * @param selfType 自己参照の場合は種別が入る。nullだと自己参照ではない
         * @return マッチしなければnull
         */
        List<TokenData> matchToken(TokenContext context, ITokenType selfType);
    }

    public abstract class AbstractParser extends AbstractEntry implements IParser {

        public void value(Function<ParserArgs, Object> resolver) {
            register(((Node) register).type, this).value(resolver);
            entryChild(this);
        }

        public AbstractParser join(CharSequence token, CharSequence... others) {
            return new PatternEntry(token, new PatternEntry(token, others));
        }

        public AbstractParser count(int count) {
            return count(count, count);
        }

        public AbstractParser count(int min, int max) {
            entryChild(this);
            AbstractParser self = this;
            return new ParserEntry((context, selfType) -> {
                List<TokenData> result = new ArrayList<>();
                int count = 0;
                TokenContext ctx = context;
                for (int i = 0; i < max; i++) {
                    List<TokenData> ret = self.matchToken(ctx, i == 0 ? selfType : null);
                    if (ret == null) {
                        break;
                    } else if (ret.size() == 0) {
                        // これ以上は同じ
                        count = max;
                        break;
                    }
                    count++;
                    result.addAll(ret);
                    ctx = ret.get(ret.size() - 1).nextContext();
                }
                if (count < min) {
                    return null;
                }
                return result;
            });
        }

    }

    /**
     * 複数をor結合する
     */
    public class ParserEntry extends AbstractParser implements CharSequence {
        private final List<IParser> parserList = new ArrayList<>();

        private ParserEntry(IParser parser, IParser... others) {
            entryChild(parser, others);
            parserList.add(parser);
            if (others.length > 0) {
                parserList.addAll(Arrays.asList(others));
            }
        }

        @Override
        public List<TokenData> matchToken(TokenContext context, ITokenType selfType) {
            List<TokenData> result = null;
            TokenContext.Index lastEnd = null;
            for (IParser parser : parserList) {
                List<TokenData> ret = parser.matchToken(context, selfType);
                if (ret != null) {
                    TokenContext.Index end;
                    if (ret.size() > 0) {
                        end = ret.get(ret.size() - 1).getEnd();
                    } else {
                        end = context.getIndex();
                    }
                    if (result == null || end.getIndex() > lastEnd.getIndex()) {
                        result = ret;
                        lastEnd = end;
                    }
                }
            }
            return result;
        }

        @Override
        public int length() {
            return 0;
        }

        @Override
        public char charAt(int index) {
            return 0;
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return null;
        }

        @Override
        public String toString() {
            return null;
        }
    }

    /**
     * トークンを and結合する
     */
    public class PatternEntry extends AbstractParser implements CharSequence {
        private final List<CharSequence> patternList = new ArrayList<>();

        private PatternEntry(CharSequence token, CharSequence... others) {
            entryChild(token, others);
            patternList.add(token);
            if (others.length > 0) {
                patternList.addAll(Arrays.asList(others));
            }
        }

        public PatternEntry or(CharSequence token, CharSequence... others) {
            // TODO
            // pattern("if", "(", CONDITION.as("if"), ")", BLOCK.as("if-b"))
            // .join(pattern("else", "if", "(", CONDITION.as("elif"), ")", BLOCK.as("elif-b")).count(0, -1)
            // .join(pattern("else", BLOCK.as("else")).count(0, 1)).param(XX.class, as("if-b", XX.class)).value(i->b->aa);
            Class<List<String>> obj;
            return null;
        }

        @Override
        public int length() {
            return 0;
        }

        @Override
        public char charAt(int index) {
            return 0;
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return null;
        }

        @Override
        public String toString() {
            return null;
        }

        @Override
        public List<TokenData> matchToken(TokenContext context, ITokenType selfType) {
            List<TokenData> result = new ArrayList<>();
            TokenContext ctx = context;
            ITokenType self = selfType;
            for (CharSequence pat : patternList) {
                if (pat instanceof ITokenType) {
                    if (self != null && !self.isSame((ITokenType) pat)) {
                        // 一致しないので処理しない
                        return null;
                    }
                    Optional<TokenData> ret = ctx.getToken((ITokenType) pat);
                    if (!ret.isPresent()) {
                        // 一致しない
                        return null;
                    }
                    if (pat instanceof ITokenType.NamedToken) {
                        // 名前付き
                        TokenData dat = ret.get();
                        if (dat instanceof TokenData.NodeToken) {
                            result.add(new NamedNodeToken((TokenData.NodeToken) ret.get(), ((ITokenType.NamedToken) pat).getVarName()));
                        } else {
                            result.add(new NamedTextToken((TokenData.TextToken) ret.get(), ((ITokenType.NamedToken) pat).getVarName()));
                        }
                    } else {
                        result.add(ret.get());
                    }
                    ctx = ret.get().nextContext();
                } else if (self != null) {
                    // 自己参照のみが対象
                    return null;
                } else if (pat instanceof IParser) {
                    List<TokenData> ret = ((IParser) pat).matchToken(ctx, self);
                    if (ret == null) {
                        return null;
                    } else if (ret.size() > 0) {
                        result.addAll(ret);
                        ctx = ret.get(ret.size() - 1).nextContext();
                    }
                } else {
                    // 文字列とする
                    Optional<TokenData.TextToken> ret = context.getToken(pat.toString());
                    if (!ret.isPresent()) {
                        return null;
                    }
                    result.add(ret.get());
                    ctx = ret.get().nextContext();
                }
                self = null;
            }
            return result;
        }
    }

    /**
     * 名前付きのノード
     */
    @ToString(callSuper = true)
    private class NamedNodeToken extends TokenData.NodeToken {
        private String varName;

        private NamedNodeToken(NodeToken src, String name) {
            super(src.getType(), src.nextContext(), src.getChildren(), s -> src.getValue());
            this.varName = name;
        }
    }

    @ToString(callSuper = true)
    private class NamedTextToken extends TokenData.TextToken {
        private String varName;

        public NamedTextToken(TextToken src, String name) {
            super(src.getType(), src.nextContext(), src.getEnd(), src.getText(), src.getValue());
            this.varName = name;
        }
    }

    private class ParserStream {
        private class ParserContext extends TokenContext {
            private final TokenContext lexerContext;

            private Map<String, TokenData> tokenDataMap = new HashMap<>();

            private List<TokenData> cacheTokenList;

            private ParserContext(TokenContext context) {
                lexerContext = context;
                // トークンをキャッシュする
                // TODO contextを変更する
                context.getTokens().stream()
                        .map(v -> {
                            TokenData.TextToken token = (TokenData.TextToken) v;
                            return new TokenData.TextToken(token.getType(), this, token.getEnd(), token.getText(), token.getValue());
                        })
                        .sorted(Comparator.comparingInt(v -> v.getText().length()))
                        .forEach(v -> tokenDataMap.put(v.getType().getTokenName(), v));
            }

            @Override
            public boolean isEof() {
                return lexerContext.isEof();
            }

            @Override
            public Index getIndex() {
                return lexerContext.getIndex();
            }

            @Override
            public String getWhiteSpace() {
                return lexerContext.getWhiteSpace();
            }

            @Override
            public TokenContext seek(int index) {
                return getContext(lexerContext.seek(index));
            }

            @Override
            public TokenContext restore(Index index) {
                return getContext(lexerContext.restore(index));
            }

            @Override
            public List<TokenData> getTokens() {
                if (cacheTokenList == null) {
                    for (String type : nodePatternMap.keySet()) {
                        getToken(ITokenType.CToken.from(type));
                    }
                    cacheTokenList = tokenDataMap.values().stream().filter(v -> v != null).collect(Collectors.toList());
                }
                return cacheTokenList;
            }

            @Override
            public Optional<TokenData.TextToken> getToken(String text) {
                return lexerContext.getToken(text).map(t -> new TokenData.TextToken(t.getType(), this, t.getEnd(), t.getText(), t.getValue()));
            }

            @Override
            public Optional<TokenData> getToken(ITokenType type) {
                String name = type.getTokenName();
                // パターンのチェック
                if (tokenDataMap.containsKey(name)) {
                    return Optional.ofNullable(tokenDataMap.get(name));
                }
                if (!nodePatternMap.containsKey(name)) {
                    return Optional.empty();
                }
                // パターンをチェックする
                // 無限ループ対策
                tokenDataMap.put(name, null);
                TokenData.NodeToken lastToken = null;
                List<NodePattern> patterns = nodePatternMap.get(name);
                // まずは自己参照以外をチェックする
                for (NodePattern pat : patterns) {
                    List<TokenData> result = null;
                    Index endIndex = null;
                    List<TokenData> ret = pat.matchToken(this, null);
                    if (ret != null && ret.size() > 0) {
                        // 入れ替えかも
                        Index end = ret.get(ret.size() - 1).getEnd();
                        if (result == null || end.getIndex() > endIndex.getIndex()) {
                            // 入れ替え
                            endIndex = end;
                            result = ret;
                        }
                    }
                    if (result != null) {
                        // 登録する
                        if (lastToken == null || endIndex.getIndex() > lastToken.getEnd().getIndex()) {
                            // 入れ替える
                            lastToken = new TokenData.NodeToken(pat.type, this, result, pat.resolver);
                            tokenDataMap.put(name, lastToken);
                        }
                    }
                }
                if (lastToken == null) {
                    // 一致しなかった
                    tokenDataMap.put(name, null);
                    return Optional.empty();
                }
                // 次に自己参照リストを処理する
                boolean modFlag = true;
                while (modFlag) {
                    modFlag = false;
                    for (NodePattern pat : patterns) {
                        List<TokenData> result = null;
                        Index endIndex = null;
                        List<TokenData> ret = pat.matchToken(this, type);
                        if (ret != null && ret.size() > 0) {
                            // 入れ替えかも
                            Index end = ret.get(ret.size() - 1).getEnd();
                            if (result == null || end.getIndex() > endIndex.getIndex()) {
                                // 入れ替え
                                endIndex = end;
                                result = ret;
                            }
                        }
                        if (result != null) {
                            if (endIndex.getIndex() > lastToken.getEnd().getIndex()) {
                                // 入れ替える
                                // TODO
                                lastToken = new TokenData.NodeToken(pat.type, this, result, null);
                                tokenDataMap.put(name, lastToken);
                                modFlag = true;
                            }
                        }
                    }
                }
                return Optional.of(lastToken);
            }
        }

        private Map<Integer, ParserContext> contextMap = new HashMap<>();

        private ParserContext getContext(TokenContext context) {
            ParserContext result = contextMap.get(context.getIndex().getIndex());
            if (result == null) {
                result = new ParserContext(context);
                contextMap.put(context.getIndex().getIndex(), result);
            }
            return result;
        }
    }

    public abstract class Node extends AbstractRegisterer<AbstractParser> {
        private final ITokenType type;
        private final List<IParser> parserList = new ArrayList<>();
        // TODO

        protected Node(ITokenType type) {
            this.type = type;
        }

        protected AbstractParser pattern(CharSequence type, CharSequence... others) {
            return new PatternEntry(type, others);
        }

        protected AbstractParser or(CharSequence type, CharSequence... others) {
            return new PatternEntry(type);
        }

        @Override
        protected void commit(List<AbstractParser> entryList) {
            if (entryList.size() > 1) {
                register(type, HkobaParser.this.or(entryList.get(0), entryList.subList(1, entryList.size()).toArray(new IParser[0])));
            } else if (entryList.size() == 1) {
                register(type, entryList.get(0));
            }
        }

        protected <T> Object exec(T arg, Function<T, Object> func) {
            return func.apply(arg);
        }

        protected <T, U> Object exec(T arg1, U arg2, Function<T, Function<U, Object>> func) {
            return func.apply(arg1).apply(arg2);
        }

        protected <T, U> Object exec(T arg1, U arg2, BiFunction<T, U, Object> func) {
            return func.apply(arg1, arg2);
        }

        protected <T, U, V> Object exec(T arg1, U arg2, V arg3, Function<T, Function<U, Function<V, Object>>> func) {
            return func.apply(arg1).apply(arg2).apply(arg3);
        }

        protected <T, U, V, W> Object exec(T arg1, U arg2, V arg3, W arg4, Function<T, Function<U, Function<V, Function<W, Object>>>> func) {
            return func.apply(arg1).apply(arg2).apply(arg3).apply(arg4);
        }
    }

    protected IParser or(IParser parser, IParser... others) {
        return (c, t) -> {
            List<TokenData> result = parser.matchToken(c, t);
            TokenContext.Index lastEnd = c.getIndex();
            if (result != null && result.size() > 0) {
                lastEnd = result.get(result.size() - 1).getEnd();
            }
            if (others.length > 0) {
                for (IParser ps : others) {
                    List<TokenData> res = ps.matchToken(c, t);
                    if (res != null) {
                        TokenContext.Index end = c.getIndex();
                        if (res.size() > 0) {
                            end = res.get(res.size() - 1).getEnd();
                        }
                        if (end.getIndex() > lastEnd.getIndex()) {
                            result = res;
                            lastEnd = end;
                        }
                    }
                }
            }
            return result;
        };
    }

    public class NodePattern implements IParser {
        private final ITokenType type;
        private final IParser parser;
        private Function<ParserArgs, Object> resolver;

        private NodePattern(ITokenType type, IParser parser) {
            this.type = type;
            this.parser = parser;
        }

        @Override
        public List<TokenData> matchToken(TokenContext context, ITokenType selfType) {
            return parser.matchToken(context, selfType);
        }

        public void value(Function<ParserArgs, Object> resolver) {
            this.resolver = resolver;
        }
    }

    protected NodePattern register(ITokenType type, IParser parser) {
        List<NodePattern> list = nodePatternMap.get(type.getTokenName());
        if (list == null) {
            list = new ArrayList<>();
            nodePatternMap.put(type.getTokenName(), list);
        }
        NodePattern result = new NodePattern(type, parser);
        list.add(result);
        return result;
    }

    private Map<String, List<NodePattern>> nodePatternMap = new HashMap<>();

    @Override
    public TokenContext getContext(String text) {
        return new ParserStream().getContext(getLexerContext(text));
    }
}
