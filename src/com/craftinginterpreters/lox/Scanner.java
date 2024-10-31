package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private static final Map<String,TokenType> keywords;
    static {
        keywords = new HashMap<>();
        keywords.put("and",TokenType.AND);
        keywords.put("class",TokenType.CLASS);
        keywords.put("else",TokenType.ELSE);
        keywords.put("false",TokenType.FALSE);
        keywords.put("for",TokenType.FOR);
        keywords.put("fun",TokenType.FUN);
        keywords.put("if",TokenType.IF);
        keywords.put("nil",TokenType.NIL);
        keywords.put("or",TokenType.OR);
        keywords.put("print",TokenType.PRINT);
        keywords.put("return",TokenType.RETURN);
        keywords.put("super",TokenType.SUPER);
        keywords.put("this",TokenType.THIS);
        keywords.put("true",TokenType.TRUE);
        keywords.put("var",TokenType.VAR);
        keywords.put("while",TokenType.WHILE);
    }

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
            case '"':string();break;
            default:
                //由于每个十进制数字都增加一个case很繁琐，因此就放到default中增加一个判断
                if(isDigit(c)){
                    //如果是数字，就开始处理整个数字
                    number();
                }else if(isAlpha(c)){
                    identifier();
                }else{
                    Lox.error(line, "Unexpected character.");
                }
                break;
        }
    }

    /*
    * 这里可以看到，Lox是支持多行字符串的，这有优势也有劣势，但是禁止多行比允许多行要复杂些(很意外，竟然只支持单行要简单些)
    * 这里同时创建了Token以及解释器后面会使用的实际字符串值。如果要支持转义序列(escape sequences)，我们需要在这里反转义
    */
    private void string() {
        //直到碰到第二个'"'以及没有到结尾，就一直消费字符串
        while (peek() !='"' && !isAtEnd()){
            if(peek() =='\n')line++;
            advance();
        }
        //如果到结尾还没有遇到第二个'"'，就报错
        if(isAtEnd()){
            Lox.error(line,"Unterminated string.");
            return;
        }
        //消费第二个'"'
        advance();
        //截取前后双引号内的数据
        String value = source.substring(start+1,current-1);
        addToken(TokenType.STRING,value);
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
        return current >= source.length();
    }


    /*
    peek的作用就是向前看(lookahead)，这里，peek只检查当前未消费的字符，因此称之为前看一个字符。这个前看的数字越少，scanner运行的越快。
    词法语法(lexical grammar)的规则确定了我们要前看多少字符。广泛使用的大部分语言只需要前看一到两个字符。
     */
    private char peek() {
        if(isAtEnd()) return  '\0';
        return source.charAt(current);
    }
    //我们是可以给peek改造成接收一个参数表示前瞻字符的数量，但是单独定一个peekNext的原因是我们想要明确的表示我们的scanner最多前看两个字符
    private char peekNext() {
        if(current+1>source.length()) return '\0';
        return source.charAt(current+1);
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

    /*
    Lox在runtime中使用浮点数，但是整数和十进制字面量都是支持的。
    数字字面量就是一串数字序列加上可选的一个'.'，以及跟着的一个或者多个数字。
    Lox不允许以'.'开头或者结尾的数字。比如".12"和"12."，前者如果支持比较好实现，
    但是后者的实现就会有很多奇怪的事情，特别是如果我们允许数字上可以有方法，比如"12.sqrt()"
     */
    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }


    /*
    先消费字面量的整数部分数字，然后查找小数部分。小数部分是以一个十进制点'.'符号跟着至少一个数字。
    如果确实有小数。我们就继续尽可能的消费数字。
    由于要越过十进制点查看，因此我们需要向前看第二个字符，因为除非我们确认'.'后面有一个数字，否则我们不想消费'.'，
    因此，需要一个peekNext方法。
    最后，我们转换整个词素为它的数字值。
    注意：书中的scanner本身会把11a这种拆分为 11 和 a。其中11是数字而a是标识符。scanner不处理这个问题，他会持续消费输入
     */
    private void number() {
        while(isDigit(peek())) advance();
        if(peek()=='.'&&isDigit(peekNext())){
            advance();
            while (isDigit(peek()))advance();
        }
        addToken(TokenType.NUMBER,Double.parseDouble(source.substring(start,current)));
    }

    //标识符以英文字符或者_开始
    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c == '_');
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c) ;
    }
    private void identifier() {
        while(isAlphaNumeric(peek())){
            advance();
        }
        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if(type == null) {
            type = TokenType.IDENTIFIER;
        }
        addToken(type);
    
    }

}
