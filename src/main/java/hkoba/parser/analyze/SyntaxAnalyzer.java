package hkoba.parser.analyze;

import hkoba.parser.TokenData;
import hkoba.parser.TokenMap;

import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SyntaxAnalyzer {
    private class ParserContext {
        private class SyntaxTokenMap extends TokenMap {
            private final TokenMap lexerTokenMap;
            private final Map<TokenData.TokenType, TokenData> tokenDataMap = new HashMap<>();
            private List<TokenData> allTokenList;

            private SyntaxTokenMap(TokenMap tokenMap) {
                lexerTokenMap = tokenMap;
                // Lexerトークンはすべてエントリする
                tokenMap.getTokens().forEach(v -> tokenDataMap.put(v.getType(), new TokenData.TextToken((TokenData.TextToken) v, this)));
            }

            @Override
            public boolean isEof() {
                return lexerTokenMap.isEof();
            }

            @Override
            public TokenData.Index getIndex() {
                return lexerTokenMap.getIndex();
            }

            @Override
            public String getWhiteSpace() {
                return lexerTokenMap.getWhiteSpace();
            }

            @Override
            public TokenMap next(int index) {
                return moveNext(lexerTokenMap, index);
            }

            @Override
            public TokenMap move(TokenData.Index index) {
                return restore(lexerTokenMap, index);
            }

            @Override
            public <T> Optional<TokenData.TextToken<T>> getTextToken(String text) {
                return lexerTokenMap.getTextToken(text).map(v -> (TokenData.TextToken<T>) new TokenData.TextToken<>(v, this));
            }

            @Override
            public <T, U extends TokenData<T>> Optional<U> getToken(TokenData.TokenType type) {
                if (tokenDataMap.containsKey(type)) {
                    return Optional.ofNullable((U) tokenDataMap.get(type));
                }
                if (!parserEntryMap.containsKey(type)) {
                    // 存在しない種別
                    return Optional.empty();
                }
                // 検索する
                // 無限ループ対策
                tokenDataMap.put(type, null);
                // 応答
                TokenData.NodeToken result = null;
                for (ParserEntryData entry : parserEntryMap.get(type)) {
                    for (int i = 0; i < entry.parsers.size(); i++) {
                        CharSequence[] types = entry.parsers.get(i);
                        TokenMap tokenMap = this;
                        List<TokenData> child = new ArrayList<>();
                        for (int j = 0; j < types.length; j++) {
                            Optional<TokenData> param;
                            if (j == 0 && type.equals(types[0])) {
                                // 自己参照
                                param = Optional.ofNullable(result);
                            } else if (types[j] instanceof TokenData.TokenType) {
                                param = tokenMap.getToken((TokenData.TokenType) types[j]);
                            } else {
                                param = Optional.ofNullable(tokenMap.getTextToken(types[j].toString()).orElse(null));
                            }
                            if (!param.isPresent()) {
                                // NG
                                break;
                            }
                            child.add(param.get());
                            tokenMap = param.get().next();
                        }
                        if (child.size() < types.length) {
                            // NG
                            continue;
                        }
                        // OK
                        if (result != null && result.getEnd().getIndex() >= tokenMap.getIndex().getIndex()) {
                            // より短かった
                            continue;
                        }
                        // 確定する
                        Object value;
                        if (entry.resolver != null) {
                            value = entry.resolver.apply(new SyntaxArgs(child));
                        } else {
                            value = null;
                        }
                        result = new TokenData.NodeToken(type, this, value, child);
                        tokenDataMap.put(type, result);
                        if (types.length > 1 && type.equals(types[0])) {
                            // 自己参照なので再度実施する
                            i--;
                        }
                    }
                }
                return Optional.ofNullable((U) result);
            }

            @Override
            public List<? extends TokenData> getTokens() {
                if (allTokenList == null) {
                    for (TokenData.TokenType type : parserEntryMap.keySet()) {
                        if (!tokenDataMap.containsKey(type)) {
                            if (!getToken(type).isPresent()) {
                                // トークンがないものは null を設定
                                tokenDataMap.put(type, null);
                            }
                        }
                    }
                    allTokenList = tokenDataMap.values().stream().filter(v -> v != null).collect(Collectors.toList());
                }
                return allTokenList;
            }
        }

        private Map<Integer, SyntaxTokenMap> cacheMap = new HashMap<>();

        private SyntaxTokenMap moveNext(TokenMap lexer, int index) {
            return getTokenMap(lexer.next(index));
        }

        private SyntaxTokenMap restore(TokenMap lexer, TokenData.Index index) {
            return getTokenMap(lexer.move(index));
        }

        private SyntaxTokenMap getTokenMap(TokenMap lexer) {
            SyntaxTokenMap result = cacheMap.get(lexer.getIndex().getIndex());
            if (result == null) {
                result = new SyntaxTokenMap(lexer);
                cacheMap.put(lexer.getIndex().getIndex(), result);
            }
            return result;
        }
    }

    private static class ParserEntryData {
        private List<CharSequence[]> parsers = new ArrayList<>();
        private Function<SyntaxArgs, Object> resolver;
    }

    public class ParserEntry {
        private final TokenData.TokenType type;
        private ParserEntryData entryData;
        private List<ParserEntryData> entryList;

        private ParserEntry(TokenData.TokenType type) {
            this.type = type;
        }

        public ParserEntry pattern(CharSequence type, CharSequence... others) {
            CharSequence[] args = new CharSequence[others.length + 1];
            args[0] = type;
            if (others.length > 0) {
                System.arraycopy(others, 0, args, 1, others.length);
            }
            if (entryList == null) {
                entryList = new ArrayList<>();
                parserEntryMap.put(this.type, entryList);
            }
            if (entryData == null) {
                entryData = new ParserEntryData();
                entryList.add(entryData);
            }
            entryData.parsers.add(args);
            return this;
        }

        public ParserEntry value(Function<SyntaxArgs, Object> resolver) {
            if (entryData == null) {
                throw new IllegalArgumentException("No Patterns");
            }
            entryData.resolver = resolver;
            entryData = null;
            return this;
        }

        public ParserEntry entry(String type) {
            return SyntaxAnalyzer.this.entry(type);
        }

        public ParserEntry entry(TokenData.TokenType type) {
            return SyntaxAnalyzer.this.entry(type);
        }
    }

    private Map<TokenData.TokenType, List<ParserEntryData>> parserEntryMap = new HashMap<>();

    public ParserEntry entry(String type) {
        return entry(TokenData.token(type));
    }

    public ParserEntry entry(TokenData.TokenType type) {
        return new ParserEntry(type);
    }

    public TokenMap getTokens(LexicalAnalyzer lexer, String text) {
        return new ParserContext().getTokenMap(lexer.getTokens(text));
    }

    public TokenMap getTokens(LexicalAnalyzer lexer, Reader reader) throws IOException {
        return new ParserContext().getTokenMap(lexer.getTokens(reader));
    }

    public static Function<SyntaxArgs, Object> defaultValue() {
        return null;
    }
}
