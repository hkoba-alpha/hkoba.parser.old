package hkoba.parser2;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * トークン種別を示すインタフェース
 */
public interface ITokenType extends CharSequence {
    String getTokenName();

    /**
     * equalsが使えないのでこれで一致するかをチェックする
     *
     * @param other
     * @return
     */
    default boolean isSame(ITokenType other) {
        return getTokenName().equals(other.getTokenName());
    }

    @Override
    default int length() {
        return getTokenName().length();
    }

    @Override
    default char charAt(int index) {
        return getTokenName().charAt(index);
    }

    @Override
    default CharSequence subSequence(int start, int end) {
        return getTokenName().subSequence(start, end);
    }

    /**
     * 変数名を設定する
     *
     * @param name
     * @return
     */
    default NamedToken as(String name) {
        return new NamedToken(name, this);
    }

    /**
     * enumで継承して定義するトークン種別
     *
     * @param <E>
     */
    interface EToken<E extends Enum<E>> extends ITokenType {
        @Override
        default String getTokenName() {
            return ((E) this).name();
        }
    }

    /**
     * クラスの実体で持つトークン種別
     */
    @EqualsAndHashCode
    class CToken implements ITokenType {
        @Getter
        private final String tokenName;

        private CToken(String name) {
            tokenName = name;
        }

        public static CToken from(String name) {
            return new CToken(name);
        }

        /**
         * 不明なトークン種別
         */
        public static final CToken UNKNOWN = new CToken("");
    }

    /**
     * 変数名付きトークン
     */
    class NamedToken extends CToken {
        @Getter
        private String varName;

        private NamedToken(String name, ITokenType type) {
            super(type.getTokenName());
            this.varName = name;
        }
    }
}
