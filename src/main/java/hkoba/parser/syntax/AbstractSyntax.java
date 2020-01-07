package hkoba.parser.syntax;

import hkoba.parser.TokenData;
import hkoba.parser.TokenMap;
import hkoba.parser.analyze.LexicalAnalyzer;
import hkoba.parser.analyze.SyntaxAnalyzer;
import lombok.Getter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public abstract class AbstractSyntax {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    protected @interface InitEntry {
        String[] value() default {};
    }

    /**
     * InitEntryの条件
     */
    @FunctionalInterface
    public interface InitCondition {
        boolean isTarget(List<String> types);
    }

    @Getter
    private LexicalAnalyzer lexicalAnalyzer;
    @Getter
    private SyntaxAnalyzer syntaxAnalyzer;

    protected AbstractSyntax() {
        lexicalAnalyzer = new LexicalAnalyzer();
        syntaxAnalyzer = new SyntaxAnalyzer();
    }

    protected AbstractSyntax(LexicalAnalyzer lexicalAnalyzer, SyntaxAnalyzer syntaxAnalyzer) {
        this.lexicalAnalyzer = lexicalAnalyzer;
        this.syntaxAnalyzer = syntaxAnalyzer;
    }

    protected AbstractSyntax(AbstractSyntax src) {
        this.lexicalAnalyzer = src.lexicalAnalyzer;
        this.syntaxAnalyzer = src.syntaxAnalyzer;
    }

    public AbstractSyntax init(InitCondition... option) {
        init(lexicalAnalyzer, syntaxAnalyzer, option);
        return this;
    }

    /**
     * デフォルトは @InitEntry を実行する
     *
     * @param lex
     * @param syntax
     * @param option
     */
    protected void init(LexicalAnalyzer lex, SyntaxAnalyzer syntax, InitCondition... option) {
        List<InitCondition> optionList = Arrays.asList(option);
        Class clazz = getClass();
        while (clazz != AbstractSyntax.class) {
            for (Method method : clazz.getDeclaredMethods()) {
                InitEntry ann = method.getDeclaredAnnotation(InitEntry.class);
                if (ann == null) {
                    continue;
                }
                List<String> types = Arrays.asList(ann.value());
                // 対象かもしれない
                if (ann.value().length > 0 && optionList.size() > 0) {
                    // 対象であるかをチェックする
                    boolean okFlag = false;
                    for (InitCondition condition : optionList) {
                        if (condition.isTarget(types)) {
                            okFlag = true;
                            break;
                        }
                    }
                    if (!okFlag) {
                        continue;
                    }
                }
                method.setAccessible(true);
                Object[] args = new Object[method.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    Class argType = method.getParameterTypes()[i];
                    if (argType.isAssignableFrom(LexicalAnalyzer.class)) {
                        args[i] = lex;
                    } else if (argType.isAssignableFrom(SyntaxAnalyzer.class)) {
                        args[i] = syntax;
                    }
                }
                try {
                    method.invoke(this, args);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    protected static TokenData.TokenType token(String type) {
        return TokenData.token(type);
    }

    protected static Function<String, String> word(String word, String... others) {
        return LexicalAnalyzer.word(word, others);
    }

    protected static Function<String, String> regex(String regex, String... others) {
        return LexicalAnalyzer.regex(regex, others);
    }

    protected static Function<String, String> left(int size) {
        return LexicalAnalyzer.left(size);
    }

    public TokenMap parse(String text) {
        return syntaxAnalyzer.getTokens(lexicalAnalyzer, text);
    }

    /**
     * 指定したいくつかがあればtrue
     *
     * @param type
     * @param others
     * @return
     */
    public static InitCondition include(String type, String... others) {
        return lst -> {
            if (lst.contains(type)) {
                return true;
            }
            for (String tp : others) {
                if (lst.contains(tp)) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * 指定したものがあればfalse
     *
     * @param type
     * @param others
     * @return
     */
    public static InitCondition exclude(String type, String... others) {
        return lst -> {
            if (lst.contains(type)) {
                return false;
            }
            for (String tp : others) {
                if (lst.contains(tp)) {
                    return false;
                }
            }
            return true;
        };
    }

    public static InitCondition or(InitCondition... conditions) {
        return lst -> {
            for (InitCondition cond : conditions) {
                if (cond.isTarget(lst)) {
                    return true;
                }
            }
            return false;
        };
    }

    public static InitCondition and(InitCondition... conditions) {
        return lst -> {
            for (InitCondition cond : conditions) {
                if (!cond.isTarget(lst)) {
                    return false;
                }
            }
            return true;
        };
    }
}
