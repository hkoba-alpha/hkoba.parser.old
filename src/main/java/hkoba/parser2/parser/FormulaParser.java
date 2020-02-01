package hkoba.parser2.parser;

import hkoba.parser2.ITokenType;

import java.math.BigDecimal;
import java.math.BigInteger;

public class FormulaParser extends HkobaParser {
    public enum FormulaType implements ITokenType.EToken {
        DIGIT,
        SECTION,
        FORMULA,
        BINARY,
        UNARY
    }

    public FormulaParser() {
        new Token(FormulaType.DIGIT) {{
            _reg("0", "[1-9][0-9]*", "[1-9][0-9]*\\.(\\d*)").value(s -> new BigDecimal(s));
            _reg("0[0-7]+").value(s -> new BigInteger(s, 8));
            _reg("0x[0-9a-fA-F]+").value(s -> new BigInteger(s, 16));
        }};

        new Token(FormulaType.BINARY) {{
            _t("+", "-", "*", "/", "**", "//");
        }};

        new Token(FormulaType.UNARY) {{
            _t("-", "+", "!", "/");
        }};

        new Node(FormulaType.SECTION) {{
            pattern(FormulaType.DIGIT).value(s -> exec(s.getValue(BigDecimal.class), v -> v));
            pattern(FormulaType.UNARY, FormulaType.SECTION.as("sect"));
            pattern("(", FormulaType.FORMULA, ")");
        }};

        new Node(FormulaType.FORMULA) {{
            pattern(FormulaType.SECTION);
            pattern(FormulaType.FORMULA, FormulaType.BINARY, FormulaType.SECTION);
        }};
    }
}
