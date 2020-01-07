package hkoba.parser;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public abstract class TokenMap {
    public abstract boolean isEof();

    public boolean hasToken(String token) {
        return getTextToken(token).isPresent();
    }

    public boolean hasToken(TokenData.TokenType token) {
        return getToken(token).isPresent();
    }

    public abstract TokenData.Index getIndex();

    public abstract String getWhiteSpace();

    /**
     * 指定したindexへ進めてトークンを取得する
     *
     * @param index
     * @return
     */
    public abstract TokenMap next(int index);

    /**
     * 指定したindexのトークンを取得する
     *
     * @param index
     * @return
     */
    public abstract TokenMap move(TokenData.Index index);

    public abstract <T> Optional<TokenData.TextToken<T>> getTextToken(String text);

    /**
     * 最も長いトークンを返す
     *
     * @return
     */
    public <T extends TokenData> Optional<T> getToken() {
        return getTokens().stream().map(v -> (T) v)
                .sorted(Comparator.comparingInt(v -> -v.getEnd().getIndex()))
                .findFirst();
        /*
        TokenData<?> result = null;
        int minIndex = -1;
        for (TokenData data : getTokens()) {
            if (data.getEnd().getIndex() > minIndex) {
                minIndex = data.getEnd().getIndex();
                result = data;
            }
        }
        return Optional.ofNullable(result);
        */
    }

    public <T, U extends TokenData<T>> Optional<U> getToken(Class<T> valueClass) {
        return getTokens().stream()
                .filter(v -> valueClass.isInstance(v.getValue()))
                .map(v -> (U) v)
                .sorted(Comparator.comparingInt(v -> -v.getEnd().getIndex()))
                .findFirst();
        /*
        TokenData<T> result = null;
        int minIndex = -1;
        for (TokenData data : getTokens()) {
            if (data.getEnd().getIndex() > minIndex) {
                if (valueClass.isInstance(data.getValue())) {
                    minIndex = data.getEnd().getIndex();
                    result = data;
                }
            }
        }
        return Optional.ofNullable(result);
        */
    }


    public <T> Optional<TokenData.TextToken<T>> getTextToken(TokenData.TokenType type) {
        return getToken(type).filter(v -> TokenData.TextToken.class.isInstance(v)).map(v -> (TokenData.TextToken<T>) v);
    }

    public abstract <T, U extends TokenData<T>> Optional<U> getToken(TokenData.TokenType type);

    public <T> Optional<TokenData.NodeToken<T>> getNodeToken(TokenData.TokenType type) {
        return getToken(type).filter(v -> TokenData.NodeToken.class.isInstance(v)).map(v -> (TokenData.NodeToken<T>) v);
    }

    public <T, U extends TokenData<T>> Optional<U> getToken(TokenData.TokenType type, Class<T> valueClass) {
        return getToken(type).filter(v -> valueClass.isInstance(v.getValue())).map(v -> (U) v);
    }

    public abstract List<? extends TokenData> getTokens();
}
