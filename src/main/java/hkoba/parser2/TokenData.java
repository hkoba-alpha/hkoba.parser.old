package hkoba.parser2;

import hkoba.parser2.parser.ParserArgs;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.function.Function;

@ToString(exclude = "context")
public abstract class TokenData {
    @Getter
    private final ITokenType type;

    @Getter
    private final TokenContext context;

    private TokenData(ITokenType type, TokenContext context) {
        this.type = type;
        this.context = context;
    }

    public abstract Object getValue();

    public TokenContext.Index getStart() {
        return context.getIndex();
    }

    public abstract TokenContext.Index getEnd();

    public TokenContext nextContext() {
        return context.restore(getEnd());
    }

    public String getWhiteSpace() {
        return context.getWhiteSpace();
    }

    @ToString(callSuper = true)
    public static class TextToken extends TokenData {
        @Getter
        private final TokenContext.Index end;

        @Getter
        private final String text;

        @Getter
        private final Object value;

        public TextToken(ITokenType type, TokenContext context, TokenContext.Index end, String text, Object value) {
            super(type, context);
            this.text = text;
            this.end = end;
            this.value = value;
        }
    }

    @ToString(callSuper = true)
    public static class NodeToken extends TokenData {
        @Getter
        private final List<TokenData> children;

        private Object value;

        private Function<ParserArgs, Object> resolver;

        public NodeToken(ITokenType type, TokenContext context, List<TokenData> children, Function<ParserArgs, Object> resolver) {
            super(type, context);
            this.children = children;
            this.resolver = resolver;
        }

        @Override
        public Object getValue() {
            if (resolver != null) {
                value = resolver.apply(new ParserArgs(children));
                resolver = null;
            }
            return value;
        }

        @Override
        public TokenContext.Index getEnd() {
            return children.get(children.size() - 1).getEnd();
        }
    }
}
