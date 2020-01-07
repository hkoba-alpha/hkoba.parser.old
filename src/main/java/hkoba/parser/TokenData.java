package hkoba.parser;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

public abstract class TokenData<T> {
    @EqualsAndHashCode
    public static class TokenType implements CharSequence {
        private final String type;

        private TokenType(String type) {
            this.type = type;
        }

        @Override
        public int length() {
            return type.length();
        }

        @Override
        public char charAt(int index) {
            return type.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return type.subSequence(start, end);
        }

        @Override
        public String toString() {
            return type;
        }
    }

    /**
     * 不明な種別
     */
    public static final TokenType UNKNOWN = new TokenType("");

    public static TokenType token(String type) {
        return new TokenType(type);
    }

    @RequiredArgsConstructor
    @Getter
    public static class Index {
        private final int index;
        private final int row;
        private final int col;

        @Override
        public String toString() {
            return "[" + index + "](" + row + "," + col + ")";
        }
    }

    @Getter
    private final TokenType type;

    private final TokenMap tokenMap;

    @Getter
    private final T value;

    private TokenData(TokenType type, TokenMap tokenMap, T value) {
        this.type = type;
        this.tokenMap = tokenMap;
        this.value = value;
    }

    public TokenMap next() {
        return tokenMap.move(getEnd());
    }

    public Index getStart() {
        return tokenMap.getIndex();
    }

    public abstract Index getEnd();

    public String getWhiteSpace() {
        return tokenMap.getWhiteSpace();
    }

    @Override
    public String toString() {
        return "<" + type + ">" + tokenMap.getIndex() + "=" + value;
    }

    public static class TextToken<T> extends TokenData<T> {
        @Getter
        private final Index end;
        @Getter
        private final String text;

        public TextToken(TokenType type, TokenMap tokenMap, Index end, String text, T value) {
            super(type, tokenMap, value);
            this.text = text;
            this.end = end;
        }

        public TextToken(TextToken<T> src, TokenMap tokenMap) {
            super(src.getType(), tokenMap, src.getValue());
            this.text = src.getText();
            this.end = src.getEnd();
        }

        @Override
        public String toString() {
            return text + super.toString();
        }
    }

    public static class NodeToken<T> extends TokenData<T> {
        @Getter
        private final List<TokenData> children;

        public NodeToken(TokenType type, TokenMap tokenMap, T value, List<TokenData> child) {
            super(type, tokenMap, value);
            this.children = child;
        }

        @Override
        public String toString() {
            return "Node{" + super.toString() + ", " + children + "}";
        }

        @Override
        public Index getEnd() {
            return children.get(children.size() - 1).getEnd();
        }
    }
}
