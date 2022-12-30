- [Scanning](#scanning)
  - [解释器框架](#解释器框架)
  - [错误处理](#错误处理)
  - [Lexeme和Tokens](#lexeme和tokens)
    - [Token类型](#token类型)
    - [字面量](#字面量)
    - [位置信息](#位置信息)
  - [正则语言和正则表达式](#正则语言和正则表达式)
  - [`Scanner`类](#scanner类)
  - [识别lexeme](#识别lexeme)
  - [更长的Lexemes](#更长的lexemes)
- [保留字和标识符](#保留字和标识符)
  - [笔记](#笔记)

## Scanning
任何语言编译/解释的第一步都是scanning。scanner将原始的源代码看作字符序列将其分组成 __tokens__。它们是组成语言语法的有意义的"单词"/"词组"。

### 解释器框架
我们先创建一个`jlox`解释器基本形状。从一个简单的类开始。
```java
public class Lox {
  public static void main(String[] args) throws IOException {
    if (args.length > 1) {
      System.out.println("Usage: jlox [script]");
      System.exit(64); 
    } else if (args.length == 1) {
      runFile(args[0]);
    } else {
      runPrompt();
    }
  }
}
```
`Lox`是一个脚本语言，所以他直接从源码执行。`jlox`支持两种方式，一个是命令行，当不传入任何参数时启动`jlox`就进入交互式的执行。一个是启动`jlox`时提供文件路径作为参数，它读取文件并执行。推出交互式命令行的方式可以通过`ctrl+D`的方式，这样会发送"end-of-file"的条件给程序。这样`readLine()`返回`null`，我们通过检查是否为空来推出循环。最终执行的核心函数如下：
```java
private static void run(String source){
    Scanner scanner = new Scanner(source);
    List<Token> tokens = scanner.scanTokens();
    for(Token token : tokens){
        //具体的业务在这里执行
    }
}
```
### 错误处理
我们在`jlox`的主类中实现错误报告函数的主要原因是我们要在主类定义一个字段`hadError`，它的作用是标识代码中有已知的错误从而不让解释器执行代码。同时，我们可以返回非0的退出码。而在交互模式中，我们需要及时重置这个值，这样，就不会因为用户的疏忽而退出整个交互式环境。

而且，将报告错误的代码和产生错误的代码分开是很好的工程实践。在编译器前端各个阶段都会检测到错误，但是知道如何呈现这些给用户并不是这些用户的工作。理想的情况是，我们有一个抽象，比如`ErrorReporter`接口传给`scanner`和`parser`，这样我们可以随意切换不同的报告策略。

### Lexeme和Tokens
如下一行`Lox`代码：
```js
var language = "lox";
```
词法分析的工作就是扫描类似上面的字符列表，将它们分组为有意义的最小序列。这样的每一个字符序列叫做 __lexeme(词素)__。上面那一行代码的lexeme就是`var `，`language `，`= `，`"lox"`和`;`。
#### Token类型
关键字(keyword)是语言语法的一部分，`parser`需要知道的不仅仅这个lexeme代表了某个标识符，而是它是一个保留字(reserved word)以及是哪个关键字。我们可以在识别lexeme的时候记住这个lexeme表示的是什么类型，这样就不需要`parser`通过比较原始lexeme和关键字字符串分类tokens了。我们针对每个关键字，操作符，标点符号和字面量都有不同的类型。参考`com.craftinginterpreters.lox.TokenType`类型。

#### 字面量
还有lexeme是针对文本值的，比如数字和字符串。由于`scanner`要扫描文本的每个字符才能正确的标识它，就可以在这个时候转换值的文本表示为后面解释器将要使用的活着的运行时对象。

#### 位置信息
我们在`Token`这个类中包含了lexeme，TokenType，字面量和行号。这些信息包括了当出现错误时需要的行信息。
```Java
class Token{
  final TokenType type;
  final String lexeme;
  final Object literal;
  final int line;
  Token(TokenType type,String lexeme,Object literal int line){
    this.type = type;
    this.lexeme = lexeme;
    this.literal = literal;
    this.line = line;
  }
  //忽略了toString
}
```
### 正则语言和正则表达式
`scanner`核心就是一个大循环。从源文件第一个字符开始，`scanner`判断这个字符属于哪个lexeme，然后消费它，以及在它之后所有属于同一个lexeme的字符。当处理完一个lexeme，他就产生一个token。然后，从下一个字符开始，直到输入/文件的结尾。消费字符序列判断匹配哪种lexeme这种方式可能会让你想到正则表达式。确定特定语言如何将字符序列分组到lexemes中的规则叫做这个语言的 __词法(lexical grammar)__。对于大多数编程语言，包括`Lox`，都是这种足够简单的语法规则，它们被分类为 [__正则语言(regular language)__](https://en.wikipedia.org/wiki/Regular_language)。这里的"正则"和正则表达式的正则是一个意思。完全可以通过正则表达式识别所有`Lox`的lexeme，他的背后有一套非常有意思的理论做支撑(比如[__Chomsky hierarchy__](https://en.wikipedia.org/wiki/Chomsky_hierarchy)和[__有限状态机()__](https://en.wikipedia.org/wiki/Finite-state_machine)，龙书中有详细的论述)。[Lex](http://dinosaur.compilertools.net/lex/)或者[FLex](https://github.com/westes/flex)这样的工具就是为此设计的，你提供一些正则表达式给它们，它们返回给你一个完整的`scanner`。

### `Scanner`类
下面的代码是`scanner`的框架：
```Java
class scanner{
  private final String source;
  private final List<Token> tokens = new ArrayList<>();
  private int start = 0;
  private int current = 0;
  private int line = 1;
  Scanner(String source){
    this.source = source;
  }

  List<Token> scanTokens(){
    while(!isAtEnd()){
      //此时，位于下一个lexeme的开始处
      start = current;
      scanToken();
    }
    tokens.add(new Token(EOF,"",null,line));
    return tokens;
  }

  private boolean isAtEnd(){
    return current >= source.length();
  }
}
```

### 识别lexeme
每轮循环扫描一个token。在这期间会遇到各种问题，比如包含了`Lox`不支持的字符序列，这些是词法层面的错误。我们在遇到这些错误的时候，可以抛出一个错误信息。注意，此时，这些错误的字符还是已经被消费(consumed)掉了。这样，我们就不会陷入到无限循环中(因为`scanner`是一个大循环，如果没有消费掉错误的字符，那么就会一直报错，一直消费不会到达结束的地方)。我们还会一直保持扫描，因为程序后面可能还有其他错误，我们一次尽可能检查的错误越多，对于用户越友好。

对于超过一个字符的token的处理，比如`!=`，由于`!`本身也是一个token，所以我们需要进一步检查下一个字符，两个字符一起是否匹配合法的`Lox`的操作符，如果匹配，我们消费这个字符，匹配为一个token。

### 更长的Lexemes
当处理`/`时，他可能时除法操作符，也可能是注释的开始，所以，当如果遇到它的时候，我们需要判断下一个字符是否依旧是`/`，如果是，我们就将整行消费掉。因此，我们需要一个方法，它不消费字符，只是 __前瞻(lookahead)__ 一下。因为它只是比较一下当前未消费的字符，我们有了 _前瞻一个字符_ 的操作。这个前瞻的数量越少，`scanner`运行的越快。而词法的规则指示了我们需要前瞻的字符个数。广泛使用的大多数语言只需要前瞻一两个字符。虽然注释也是lexeme，但是程序的运行并不关心它。

当我们处理字面量的时候，我们会将字面量转换为后面解释器将会用到的真实的值。`Lox`支持多行`String`，如果`Lox`支持转义字符，那么也是会在这里做反转义。`Lox`的所有数字在运行时都是浮点数。但是支持整数和小数字面量。数字字面量有一系列数位(0,1,2,3,4,5,6,7,8,9)和可选的`.`和一到多个数位组成。`Lox`不支持前导和末尾小数点。`.12`和`12.`都是非法的。

从上面的描述可以知道，`Lox`定义的数字字面量是以数位开头的，所以`-123`并不是数字字面量，它是一个表达式，将`-`应用到了数字`123`。结果通常是一样的，但是，如果我们支持对数字调用方法，那么对于如下代码：
```
print -123.abs();
```
会打印`-123`。因为取负数的操作优先级低于方法调用。我们可以让`-`作为方法的一部分，但是考虑：
```
var n = 123;
print -n.abs();
```
依旧会是打印`-123`，这样，语言看起来行为就不一致了(我觉得还好？)。而不支持末尾小数点的原因是，如果我们支持数字可以调用方法，那么对于`123.sqrt()`这种调用就需要做一些特殊的判断了。

在解析数字字面量的时候，会跟String一样，将lexeme转换为实际的数值。使用Java的`Double`类型标识数字。

## 保留字和标识符
`boolean`和`nil`我们都当作是关键字处理。注意，当出现多个词法规则都可以匹配`scanner`正在处理的代码的时候，我们应该遵守[__maximal munch__](https://en.wikipedia.org/wiki/Maximal_munch)原则，匹配最多字符的规则获胜。因此在处理标识符的时候，我们尽可能的读取字符，然后生成标识符，并判断他是不是关键字，如果是，就认为是关键字类型的token，否则是标识符类型的token。正因为处理关键字其实也是标识符，只不过是语言使用的，因此也叫做 __保留字(reserved word)__。


### 笔记
实现了本章的`Scanner`后，就可以把类似`var a = 1+2`这样的代码解析成Token，注意，此处只会按照词法规则只会根据TokenType分割。因此，如果代码是`var a = 1+12"a"`，他会拆成的tokens是，`var`,`a`,`=`,`1`,`+`,`12`,`"a"`。这里不会校验`12"a"`是不合法的一个数字，因为根据`Scanner`的逻辑，它在处理`12"a"`这个lexeme，会在消费到第一个`"`的时候，就将`12`处理为数字，然后将`"a"`处理为标识符。`Scanner`不认为`12"a"`是一个非法的数字。所以，并不会产生转换错误(即认为`12"a"`是一个整体，然后认为是无法转换的数字报错)。