package com.craftinginterpreters.lox;

/*
词法分析做法就是扫描源码文件字符列表，将它们分组为有某种意义的最小序列。每一个这样的字符块就叫做词素(lexeme)。
例如，在Lox语言中：
var language = "lox" ;
var，language，=，"lox"和;都是词素。
词素只是源代码的原始子字符串。在分组字符序列为词素的过程中，我们依旧可以发现其他有用的信息。
当我们将词素和其他信息结合在一起，就组成了token。它包含了几个有用的信息，比如：
1.token type：关键字是语言句法(grammar)的形状的一部分，解析器(parser)想知道的不仅仅是一个lexeme某个标识符(identifier)
还想知道他是一个保留字(reserved word)，以及是哪个关键字。解析器可以从原始的词素中通过字符串比较分类token，但是我们在识别词素
的过程中记住这个词素代表的类型。
对于每个关键字，操作符，部分标点符号和字面量类型，都准备不同的type枚举值
2.字面量值：存在是字面量值的词素--数字，字符串等等。由于扫描器为了正确的标记字面量已经访问了字面量的每一个字符，它也可以将一个值的
文本表示转换为活着的运行时对象给解释器在后面使用
3.位置信息：在错误报告中，解释器需要告诉用户错误的位置信息。位置信息的跟踪就是从这里开始。
 */
public class Token {
    final TokenType type;
    final String lexeme;
    final Object literal;
    final int line;

    public Token(TokenType type, String lexeme, Object literal, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
    }

    public String toString() {
        return type + " " + lexeme + " " + literal;
    }
}
