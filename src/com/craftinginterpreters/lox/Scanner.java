package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

/*
scanner的核心是一个循环。从源码的第一个字符开始，scanner找到这个字符应该属于的词素，然后消费它以及后续所有属于这个词素一部分的字符。
当scanner到了词素的结尾，它就发出一个token。
接着，scanner继续循环这一过程，从源码中下一个字符开始，消费字符产出token，直到文件结尾。这一操作非常类似于正则表达式。

确定某一个特定语言如何将字符分组为词素的规则叫做这个语言的词法语法(lexical grammar)。在大多数语言中，包括Lox，这一语法的规则足够简单，
这一类语言就分类为正则语言(regular language)。这个正则表达式中的正则是一个意思。

你可以使用正则表达式识别出Lox所有的词素。Lex和Flex这样的工具是明确设计成这个样子的，给它们一个些正则表达式，它们返回一个完整的scanner。
参考资料Chomsky hierarchy(https://en.wikipedia.org/wiki/Chomsky_hierarchy)和finite-state machine(https://en.wikipedia.org/wiki/Finite-state_machine)。
参看书：Compilers:Principles,Techniques and Tools。
 */
public class Scanner {
    private final String source;

    private final List<Token> tokens = new ArrayList<>();

    private int start = 0;
    private int current = 0;
    private int line = 1;

    public Scanner(String source) {
        this.source = source;
    }

    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            //single char token
            case '(':
                addToken(TokenType.LEFT_PAREN);
                break;
            case ')':
                addToken(TokenType.RIGHT_PAREN);
                break;
            case '{':
                addToken(TokenType.LEFT_BRACE);
                break;
            case '}':
                addToken(TokenType.RIGHT_BRACE);
                break;
            case ',':
                addToken(TokenType.COMMA);
                break;
            case '.':
                addToken(TokenType.DOT);
                ;
                break;
            case '-':
                addToken(TokenType.MINUS);
                break;
            case '+':
                addToken(TokenType.PLUS);
                break;
            case ';':
                addToken(TokenType.SEMICOLON);
                break;
            case '*':
                addToken(TokenType.STAR);
                break;
            case '/':
                /*
                处理除法操作符的时候，要考虑注释文本的情况，如果前向操作符依旧是/，那么说明这是单行注释
                单行注释的处理就会一直消费字符直到一行结束。
                这是我们处理长的词素的通用策略。当我们检测到某个词素的开头时，就转移到这个词素特定处理代码去，消费字符直到结束。
                 */
                if(match('/')){
                 // "//"此符号忽略整行 ，没有处理"/**/"这种
                 while(peek() !='\n'&& !isAtEnd()) advance();
                }else{
                    addToken(TokenType.SLASH);
                }
                break;
            //一个或者两个char的token
            case '!':
                addToken(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG);
                break;
            case '=':
                addToken(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
                break;
            case '<':
                addToken(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
                break;
            case '>':
                addToken(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);
                break;
            case '\r':
            case '\t':
            case ' ':
                //忽略空白符
                break;
            case '\n':
                //换行增加行数
                line++;
                break;

            default:
                Lox.error(line, "Unexpected character.");
                break;
        }
    }

    /*
    peek的作用就是向前看(lookahead)，这里，peek只检查当前未消费的字符，因此称之为前看一个字符。这个前看的数字越少，scanner运行的越快。
    词法语法(lexical grammar)的规则确定了我们要前看多少字符。广泛使用的大部分语言只需要前看一到两个字符。
     */
    private char peek() {
        if(isAtEnd()) return  '\0';
        return source.charAt(current);
    }

    /*
    如果匹配不上期望的字符，那么就不消费这个字符，返回false，否则向前消费一个字符
     */
    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;
        current++;
        return true;
    }

    private void addToken(TokenType tokenType) {
        addToken(tokenType, null);
    }

    private void addToken(TokenType tokenType, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(tokenType, text, literal, line));
    }

    private char advance() {
        return source.charAt(current++);
    }

    private boolean isAtEnd() {
        return current > source.length();
    }
}
