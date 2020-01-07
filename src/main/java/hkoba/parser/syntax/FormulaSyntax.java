package hkoba.parser.syntax;

import hkoba.parser.TokenData;
import hkoba.parser.analyze.LexicalAnalyzer;
import hkoba.parser.analyze.SyntaxAnalyzer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

public class FormulaSyntax extends AbstractSyntax {
    public static final TokenData.TokenType NUMBER = token("数値");
    public static final TokenData.TokenType BINARY = token("二項演算子");
    public static final TokenData.TokenType UNARY = token("単項演算子");
    public static final TokenData.TokenType FORMULA = token("計算式");
    public static final TokenData.TokenType SECTION = token("単項");

    public interface IValue {
        BigDecimal getValue();
    }

    public static class NumericValue implements IValue {
        @Getter
        private BigDecimal value;

        public NumericValue(BigDecimal value) {
            this.value = value;
        }

        public NumericValue(BigInteger value) {
            this.value = new BigDecimal(value);
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    @RequiredArgsConstructor
    public static class UnaryValue implements IValue {
        private final String name;
        private final Function<BigDecimal, BigDecimal> operation;

        private IValue unaryValue;
        private BigDecimal value;

        public BigDecimal getValue() {
            if (value == null) {
                value = operation.apply(unaryValue.getValue());
            }
            return value;
        }

        public UnaryValue setValue(IValue unary) {
            unaryValue = unary;
            return this;
        }

        @Override
        public String toString() {
            return "(" + name + unaryValue + ")";
        }
    }

    @RequiredArgsConstructor
    public static class BinaryValue implements IValue {
        private final String name;
        private final int priority;
        private final BiFunction<BigDecimal, BigDecimal, BigDecimal> operation;
        private IValue left;
        private IValue right;
        private BigDecimal value;

        public BigDecimal getValue() {
            if (value == null) {
                value = operation.apply(left.getValue(), right.getValue());
            }
            return value;
        }

        public BinaryValue setValues(IValue left, IValue right) {
            if (left instanceof BinaryValue) {
                // 優先度チェック
                BinaryValue left2 = (BinaryValue) left;
                if (left2.priority < this.priority) {
                    // 優先度が高かったので入れ替え
                    left2.right = setValues(left2.right, right);
                    return left2;
                }
            }
            this.left = left;
            if (right instanceof BinaryValue) {
                BinaryValue right2 = (BinaryValue) right;
                if (this.priority >= right2.priority) {
                    // 入れ替え
                    right2.left = setValues(left, right2.left);
                    return right2;
                }
            }
            this.right = right;
            return this;
        }

        @Override
        public String toString() {
            return "(" + left + name + right + ")";
        }
    }

    @RequiredArgsConstructor
    public static class WrapValue implements IValue {
        private final IValue value;

        public BigDecimal getValue() {
            return value.getValue();
        }

        @Override
        public String toString() {
            return "(" + value + ")";
        }
    }

    public FormulaSyntax() {
    }

    public FormulaSyntax(LexicalAnalyzer lexicalAnalyzer, SyntaxAnalyzer syntaxAnalyzer) {
        super(lexicalAnalyzer, syntaxAnalyzer);
    }

    public FormulaSyntax(AbstractSyntax src) {
        super(src);
    }

    @InitEntry
    public void initNumeric(LexicalAnalyzer lex) {
        lex.entry(NUMBER)
                .pattern(regex("[1-9]\\d*", "[1-9]\\d*\\.\\d*", "0\\.\\d*"))
                .value(s -> new NumericValue(new BigDecimal(s)))
                .pattern(regex("0b[01]+"))
                .value(s -> new NumericValue(new BigInteger(s, 2)))
                .pattern(regex("0[0-7]+"))
                .value(s -> new NumericValue(new BigInteger(s, 8)))
                .pattern(regex("0x[0-9a-fA-F]+"))
                .value(s -> new NumericValue(new BigInteger(s, 16)));
    }

    @InitEntry
    public void initBinary() {
        entryBinary("*", 11, (v1, v2) -> v1.multiply(v2));
        entryBinary("/", 11, (v1, v2) -> v1.divide(v2, 15, BigDecimal.ROUND_HALF_UP));
        entryBinary("+", 10, (v1, v2) -> v1.add(v2));
        entryBinary("-", 10, (v1, v2) -> v1.subtract(v2));
    }

    @InitEntry
    public void initUnary() {
        entryUnary("-", v -> v.negate());
        entryUnary("+", v -> v);
    }

    @InitEntry
    public void initSyntax(SyntaxAnalyzer syntax) {
        syntax.entry(SECTION)
                .pattern(NUMBER).value(arg -> arg.getValue(IValue.class).get())
                .pattern(UNARY, SECTION).value(arg -> arg.process(UnaryValue.class, IValue.class, arg1 -> arg2 -> arg1.setValue(arg2)).execute().get())
                .pattern("(", FORMULA, ")").value(arg -> new WrapValue(arg.getValue(IValue.class).get()))
                .entry(FORMULA)
                .pattern(SECTION).value(arg -> arg.getValue(IValue.class).get())
                .pattern(FORMULA, BINARY, SECTION).value(arg -> arg.process(IValue.class, BinaryValue.class, IValue.class, l -> ope -> r -> ope.setValues(l, r)).execute().get());
    }

    public FormulaSyntax entryBinary(String ope, int priority, BiFunction<BigDecimal, BigDecimal, BigDecimal> exec) {
        getLexicalAnalyzer().entry(BINARY)
                .pattern(word(ope))
                .value(s -> new BinaryValue(ope, priority, exec));
        return this;
    }

    public FormulaSyntax entryUnary(String ope, Function<BigDecimal, BigDecimal> exec) {
        getLexicalAnalyzer().entry(UNARY)
                .pattern(word(ope))
                .value(s -> new UnaryValue(ope, exec));
        return this;
    }
}
