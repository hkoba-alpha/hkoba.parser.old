package hkoba.parser2;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public abstract class TokenContext {
    @Getter
    @RequiredArgsConstructor
    @EqualsAndHashCode
    @ToString
    public static class Index {
        private final int index;
        private final int row;
        private final int col;
    }

    public abstract boolean isEof();

    public boolean hasToken(String text) {
        return getToken(text).isPresent();
    }

    public boolean hasToken(ITokenType type) {
        return getToken(type).isPresent();
    }

    public abstract Index getIndex();

    public abstract String getWhiteSpace();

    /**
     * 指定したindexへ進める。
     * 現在より後ろにしか進められない。
     *
     * @param index
     * @return
     */
    public abstract TokenContext seek(int index);

    /**
     * 指定したIndexを復元する
     *
     * @param index
     * @return
     */
    public abstract TokenContext restore(Index index);

    /**
     * 最も長く一致したトークンを取得する
     *
     * @return
     */
    public Optional<TokenData> getToken() {
        return getTokens().stream()
                .sorted(Comparator.comparingInt(t -> -t.getEnd().getIndex()))
                .findFirst();
    }

    public Optional<TokenData.TextToken> getToken(String text) {
        return getTokens().stream()
                .filter(t -> t instanceof TokenData.TextToken)
                .map(t -> (TokenData.TextToken) t)
                .filter(t -> text.equals(t.getText()))
                .sorted(Comparator.comparingInt(t -> -t.getEnd().getIndex()))
                .findFirst();
    }

    public Optional<TokenData> getToken(ITokenType type) {
        return getTokens().stream()
                .filter(t -> type.isSame(t.getType()))
                .sorted(Comparator.comparingInt(t -> -t.getEnd().getIndex()))
                .findFirst();
    }

    /**
     * 不明種別以外のトークン一覧を取得する
     *
     * @return
     */
    public abstract List<TokenData> getTokens();
}
