package com.craftinginterpreters.lox;

public enum TokenType {
    //单字符tokens
    //( ) { }
    LEFT_PAREN,RIGHT_PAREN,LEFT_BRACE,RIGHT_BRACE,
    //, . - + ; / *
    COMMA,DOT,MINUS,PLUS,SEMICOLON,SLASH,STAR,

    //一个或者两个字符tokens
    //! !=
    BANG,BANG_EQUAL,
    //= ==
    EQUAL,EQUAL_EQUAL,
    //> >=
    GREATER,GREATER_EQUAL,
    //< <=
    LESS,LESS_EQUAL,

    //字面量
    //标识符，字符串，数字
    IDENTIFIER,STRING,NUMBER,

    //关键字
    AND,CLASS,ELSE,FALSE,FUN,FOR,IF,NIL,OR,
    PRINT,RETURN,SUPER,THIS,TRUE,VAR,WHILE,

    //标识源码文件结束标识
    EOF
}
