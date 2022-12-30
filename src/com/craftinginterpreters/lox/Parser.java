package com.craftinginterpreters.lox;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/*
 * 当前是Parsing Expressions一章的内容
 * ---- 2022-12-22 ----
 * 用来解析token序列
 * 如果要完全无歧义的解析表达式就需要给表达式设定优先级以及结合性。Lox的结合性与C语言一样。
 * 优先级决定了在包含了不同操作符的表达式中，哪个操作符先被估值。
 * 结合性决定了在包含了相同(这里的相同应该也有包括优先级相同的意思)的操作符的表达式中，哪个操作符先被估值。
 * 通过设计语法，对于优先级不同的表达式指定不同的产生式，并按照优先级从上往下排(高优先级在下面)。
 * 每个规则的匹配方式就是只匹配和自己优先级一样或者比自己优先级更高的表达式。
 * Parser类使用的解析方式是递归下降(recursive descent),它是自顶向下解析的。
 * 自顶向下就是上文说的，我们的表达式的语法产生式(production)的排列顺序是低优先级的在上，高优先级的在下面。
 * 注意，由于使用了递归下降的解析方式，因此，我们的语法规则要尽量避免是左递归的(left-recursive)
 * 左递归的(left-recursive)的意思就是当前规则的规则名在生成式中位于操作符的左边而不是右边。
 * 
 * 错误处理
 * Lox使用的错误恢复机制是panic模式。只要解析器检测到了错误，就进入到panic模式。
 * 在它回到解析过程前，解析器需要让它的状态和即将到来的tokens序列对齐，这样下一个token才能匹配被解析的规则。这个过程叫做 同步(synchronization)
 * 我们会将语法中的某些规则设为同步点，当解析器遇到错误，会从嵌套中跳出到这些作为同步点的规则处，从而修复自己的状态。
 * 传统的同步地方是语句之间，目前还没有实现语句，只是一个大致的处理框架。
 * 递归下降的解析时，解析器的状态没有显式的存储而是位于调用栈上。每个规则在被解析的中间都是一个调用栈。
 * 当需要清理这些状态时，使用Java的异常处理机制清除这些调用栈从而重置状态是一个自然的方式。
 * 由于我们是在语句边界设置的同步，因此我们就在处理语句的地方捕获这些异常。异常捕获后，解析器就处于正确的状态了。之后就是同步这些tokens了。
 * 我们会丢弃新语句开始之前的所有tokens。Lox使用分号结束一个语句。大多数语句是以一个关键字(for,if,return,var)开始的。
 * 当下一个token是这些关键字，我们就可能开启了一个语句。
 */
public class Parser {
    private final List<Token> tokens;
    private int current = 0;

    // parser内的方法通过这个类确定是否unwind解析器
    // 当出现这个异常时，可能解析器并不会处于奇怪的状态，此时就不需要同步了
    private static class ParseError extends RuntimeException {
        ParseError() {
            super();
        }
    }

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Expr parse(){
        try{
            return expression();
        }catch(ParseError error){
            //语法错误恢复是parser需要处理的，所以在这里要catch，不传播给解释器其他地方
            //parser承诺了在遇到错误不会crash/hang，但是不保证返回可用的语法树
            //只要parser报告了错误，设置了hadError，后续阶段就被跳过了
            //设置hadError就在调用了Lox的error方法，里面的report设置了它
            return null;
        }
    } 

    /*
     * 解析规则的方式是：
     * terminal就会匹配和消费一个token
     * non-terminal就调用这个规则的函数
     * | 解析成if或者switch语句
     * * 或者 + 解析成while或者for循环
     * ? 解析成if
     */
    // parse expression rule
    private Expr expression() {
        return equality();
    }

    // 二元操作符的规则都是类似的，左边是更高优先级的操作符规则，中间是操作符，右边是更高优先级的操作符规则
    // 规则中如果左操作数是non-terminal，因此就解析为调用解析对应non-terminal规则的方法将结果保存到临时变量中作为左操作数
    // 规则中的(...)*解析为while循环，当不匹配TokenType列表时退出
    // 循环中会消费操作符，并调用non-terminal的解析方法生成右操作数，并和左操作数结合组成一个表达式
    // 当结束循环后，我们就将表达式组成了一左关联的嵌套树结果
    // 如果表达式遇到了 TokenType列表中的操作符 就会进入到while中，否则就会返回non-terminal的结果
    // 如此，对应规则的解析方法就匹配了自身以及更高优先级的表达式了
    private Expr parseBinary(Supplier<Expr> higherRuleParsingMethodSupplier, TokenType... types) {
        Expr expr = higherRuleParsingMethodSupplier.get();
        while (match(types)) {
            // 如果match了,注意到match会消费一个token，因此
            // 操作符应该取previous()
            Token operator = previous();
            Expr right = higherRuleParsingMethodSupplier.get();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    // 比较当前token是不是属于传入的指定TokenTypes之一
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    // 比较当前的token是不是传入的指定类型
    private boolean check(TokenType type) {
        if (isAtEnd())
            return false;
        return peek().getType() == type;
    }

    // advance会消费一个token并返回这个token
    private Token advance() {
        if (!isAtEnd())
            current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().getType() == TokenType.EOF;
    }

    // 返回我们要消费的当前token
    private Token peek() {
        return tokens.get(current);
    }

    // 返回最近消费过的token
    private Token previous() {
        return tokens.get(current - 1);
    }

    // parse equality rule "equality -> comparison(("!="|"=="")comparison)*;"
    private Expr equality() {

        return parseBinary(this::comparison, TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL);
        // Expr expr = comparison();
        // while(match(TokenType.BANG_EQUAL,TokenType.EQUAL_EQUAL)){
        // 如果match了,注意到match会消费一个token，因此
        // 操作符应该取previous()
        // Token operator = previous();
        // Expr right = comparison();
        // expr = new Expr.Binary(expr, operator, right);
        // }
        // return expr;
    }

    // 解析comparison规则 "comparison → term ( (">"|">="|"<"|"<=") term)*;"
    private Expr comparison() {
        return parseBinary(this::term, TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS,
                TokenType.LESS_EQUAL);
        // Expr expr = term();

        // while(match(TokenType.GREATER,TokenType.GREATER_EQUAL,TokenType.LESS,TokenType.LESS_EQUAL)){
        // Token operator = previous();
        // Expr right = term();
        // expr = new Expr.Binary(expr, operator, right);
        // }
        // return expr;
    }

    // 解析term规则 "term → factor( ("-"/"+") factor)*;"
    private Expr term() {
        return parseBinary(this::factor, TokenType.MINUS,TokenType.PLUS);
        // Expr expr = factor();
        // while(match(TokenType.PLUS,TokenType.MINUS)){
        // Token operator = previous();
        // Expr right = factor();
        // expr = new Expr.Binary(expr, operator, right);
        // }
        // return expr;
    }

    // 解析factor规则 "factor → unary( ("/"|"*") unary) *;"
    private Expr factor() {
        return parseBinary(this::unary, TokenType.SLASH, TokenType.STAR);
        // Expr expr = unary();
        // while(match(TokenType.SLASH,TokenType.STAR)){
        // Token operator = previous();
        // Expr right = unary();
        // expr = new Expr.Binary(expr, operator, right);
        // }
        // return expr;
    }

    // 解析一元表达式unary
    // unary → ("!"|"-") unary
    // | primary;
    private Expr unary() {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            Token operator = previous();
            // 注意到，这里就出现了递归
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        return primary();
    }

    // 解析 primary → NUMBER|STRING|"true"|"false"|"nil"|"(" expression ")";
    private Expr primary() {
        if (match(TokenType.FALSE))
            return new Expr.Literal(false);
        if (match(TokenType.TRUE))
            return new Expr.Literal(true);
        if (match(TokenType.NIL))
            return new Expr.Literal(null);
        if (match(TokenType.NUMBER, TokenType.STRING)) {
            return new Expr.Literal(previous().getLiteral());
        }

        if (match(TokenType.LEFT_PAREN)) {
            Expr expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression");
            return new Expr.Grouping(expr);
        }

        //此时，是一个无法开启一个表达式的token
       throw error(peek(), "Expect expression");
    }

    private Token consume(TokenType type, String message) {
        if (check(type))
            return advance();
        throw error(peek(), message);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        //调用此方法时已经遇到了错误，此时当前的token已经被消费了，前进一个token看下
        //是否属于分号或者另一个语句开始，如果是，说明可以继续新的解析，如果不是
        //丢弃这个token
        advance();
        while (!isAtEnd()) {
            if (previous().getType() == TokenType.SEMICOLON)
                return;
            switch (peek().getType()) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }
            advance();
        }
    }
}
