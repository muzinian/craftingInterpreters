## 解析表达式
### 歧义与解析游戏
给出一个由一系列 token 组成的字符串，我们将这些 token 映射为语法中的终结符，查看那个规则可以生成这样的字符串。完全有可能创建一个语法是有 _歧义的_，选择不同的生成式可以得出同样的字符串。解析过程中出现歧义会导致解析器错误的理解用户代码。在我们解析过程中，我们不仅仅判断一个字符串是否是一个合法的 Lox 代码，我们还需要跟踪字符串每个部分匹配的是哪个规则，这样我们知道每个 token 属于这个语言的哪个部分。目前，我们有的语法规则如下：
```
expression → literal
           | unary
           | binary
           | grouping;
literal    → NUMBER|STRING|"true"|"false"|"nil";
grouping   → "(" expression ")";
unary      → ("-"|"!") expression;
binary     → expression operator expression;
operator   → "=="|"!="|"<"|"<="|">"|">="|"+"|"-"|"*"|"/"; 
```
加入有一个合法的字符串`6/3-1`，目前，我们有两个规则可以生成出来，第一个是：
1. 从`expression`开始，选择`binary`
2. 对于左手边的`expression`，选择`NUMBER`，并使用`6`
3. 对于操作符，选择`/`
4. 对于右手边的`expression`，再次选择`binary`
5. 在这个嵌套的`binary`表达式中，选择`3-1`
第二个是：
1. 从`expression`开始，选择`binary`
2. 对于左手边的`expression`，再次选择`binary`
3. 在这个嵌套的`binary`表达式中，选择`6/3`
4. 回到外层的`binary`，对于操作符，选择`-`
5. 对于右手边的`expression`，选择`NUMBER`，并使用`1`
这两个都产生了相同的 _字符串_，但是，他们不是同一个 _语法树_。换句话说，这个语法允许将表达式看成`(6/3)-1`或者`6/(3-1)`。`binary`允许你以任意的方式嵌套操作数。这接着影响了对解析树估值的结果。这个歧义通过定义规则的优先级和结合性解决了。

* __优先级(Precedence)__ 如果一个表达式总混合了不同的操作符，优先级决定了哪个操作符先估值。优先级告诉我们`/`的估值先于`-`。高优先级的操作符比低优先级的操作符先估值。等价的说法式，高优先级的操作符"绑定的更紧"
* __结合性(Associativity)__ 在一系列相同的操作符中，哪个操作符先估值。当一个操作符是 __左结合(left-associativity)__ (认为是"从左到右")，在左边的操作符比在右边的操作符先执行。因为`-`是左关联的，表达式`5-3-1`等价于`(5-3)-1`而不是`5-(3-1)`。另一方面，赋值是 __右结合(right-associativity)__ 的，表达式`a = b = c`等价于`a = ( b = c)`。

>有些语言指定了某些操作符之间没有相对的优先级，如果表达式中混合使用了这些操作符却没有显式的分组，就会报告一个语法错误。同样，某些操作符是 __无结合性(non-associative)__。这意味着在一个序列中使用这些操作符超过一次是一个错误。比如，Perl的范围操作就没有结合性，因此`a..b`是对的，但是`a..b..c`是错的。

Lox 的优先级跟 C 语言一样。有了优先级，在使用了多个操作符的表达式中才不会出现歧义。
|Name|Operators|Associates|
|-|-|-|
|Equality|== !=|Left|
|Comparison|> >= < <=|Left|
|Term|- +|Left|
|Factor|/ *|Left|
|Unary|! -|Right|

目前，语法将所有表达式类型都塞到了单个`expression`规则中。相同的规则被用来作为非终结的操作数，这允许语法接收任意类型的表达式作为子表达式，无论优先级规则是否允许它的存在。

我们通过修改语法为每个优先级定义分开的规则解决这个问题。
```
expression → ...
equality   → ...
comparison → ...
term       → ...
factor     → ...
unary      → ...
primary    → ...
```
注意到，我们是把之前的`operator`规则拆开了，并按照优先级从下到上排列。每一个规则只会匹配比它自身高或者跟自身优先级一样的表达式。例如，`unary`只匹配一元表达式，比如`!negated`或者`primary`表达式比如`1234`。而`term`可以匹配`1+2`和`3*4/5`。最后的`primary`规则覆盖了所有的最高优先级形式——字面量或者被小括号括起来的表达式。
>有些解释器生成器允许你定义有歧义的语法(这样语法会很简单)，然后在语法上显式的添加操作符优先级元数据消除歧义。同样，当我们扩展这个语法包含赋值和逻辑操作时，我们需要修改`expression`的生成式，而不需要修改包含表达式的所有规则

我们从最简单的`expression`规则开始，一步一步的填充整个规则。总是需要有一个规则应该匹配所有表达式而不关心表达式的操作符的优先级，`expression`规则可以匹配任意优先级的所有表达式。而`equality`操作符(`==`,`!=`)的优先级最低，所以我们让`expression`规则匹配它，它覆盖了其他所有表达式：
```
expression → equality ;
```
>当然，其实是可以不用`expression`而是只用`equality`，但是前者可以让规则更易读些。

而在优先级表的另一端，`primary`表达式包含了所有字面量和分组表达式：
```
primary → NUMBER|STRING|"true"|"false"|"nil"|"(" expression ")";
```
`unary`表达式以一元操作符开始，后跟一个操作数，而且可以嵌套，即操作数可以也可以是一个一元操作符，例如，`!!true`。一个错误的规则如下：
```
unary → ("!"|"-") unary ;
```
由于嵌套，`unary`需要是递归的，但是上面的规则的问题是无法结束。每一个规则都需要匹配位于对应优先级 _或者更高_ 的表达式，因此，可以在`unary`中加入匹配一个`primary`表达式
```
unary → ("!"|"-") unary
      | primary;
```

接着考虑二元操作符。我们从乘法和除法的规则开始：
```
factor → factor ("/"|"*") unary
       | unary;
```
上面的规则，递归的匹配做操作数(注意，左递归)。这让这个规则可以匹配一系列的乘法和除法表达式(比如，`1*2/3`)。将递归生成式放在左侧，将一元表达式放在右侧让规则时左关联并且无歧义。这个规则本身没有问题，但是由于它是 __左递归的(left-recursive)__，包括这里介绍的一些解析技术处理左递归很麻烦。在其他地方递归没有问题(比如，`unary`中我们就在右侧递归的使用了`unary`，而在`primary`中为分组提供了间接的递归)，但是左递归，就不太好处理。我们可以为同一个语言选择别的匹配语法，上面的规则本身没有错，但是对于我们解析它的方式来说，不是最优的。因此：
```
factor → unary(("/"|"*") unary) *;
```
我们定义`factor`表达式为一个平坦的乘法和除法序列。它和上一个规则一样，但是很好的反映了我们解析Lox的代码。其他二元操作符的语法规则类似，完整的语法规则如下：
```
expression → equality ;
equality   → comparison (("=="|"!=") comparison)*;
comparison → term ( (">"|">="|"<"|"<=") term)*;
term       → factor( ("-"/"+") factor)*;
factor     → unary( ("/"|"*") unary) *;
unary      → ("!"|"-") unary
           | primary;
primary    → NUMBER|STRING|"true"|"false"|"nil"|"(" expression ")";
```
这样，我们就消除了之前语法的歧义了。

### 递归下降解析
常见的解析技术有 [LL(k)](https://en.wikipedia.org/wiki/LL_parser)， [LR(1)](https://en.wikipedia.org/wiki/LR_parser)和 [ LALR ](https://en.wikipedia.org/wiki/LALR_parser) ，使用了像是 [ parser combinators ](https://en.wikipedia.org/wiki/Parser_combinator) ，[ Earley parsers ](https://en.wikipedia.org/wiki/Earley_parser) ， [the shunting yard algorithm](https://en.wikipedia.org/wiki/Shunting-yard_algorithm) 和 [ packrat parsing](https://en.wikipedia.org/wiki/Parsing_expression_grammar) 技术。对于我们的解释器，__递归下降(Recursive descent)__ 足够了。

递归下降是构建解析器最简单的方式，不需要使用复杂的解析器生成器工具(比如，Yacc，Bison和ANTLR)。它简单，但是快速，健壮可以支持复杂的错误处理。很多产品级语言实现都是使用的递归下降(GCC,V8,Roslyn)。

递归下降是一个 __自顶向下解析器(top-down parser)__，他从顶层或者最外层的语法规则开始，然后会一直向下进入到嵌套的子表达式直到最后遇到语法树的叶子节点。而像是LR这样自底向上的解析器会从`primary`表达式开始组装他们为越来越大的语法块。
>由于解析处理的方式是按照语法向下处理，所以叫做递归下降。注意，优先级的高低跟位置顶和底是相反的。
>!["解析的方向"](direction.png)

递归下降解析器就是按照语法规则直接翻译为命令式代码。每个规则都会是一个函数。规则体转换为代码大概类似于：
|语法规则|代码表示|
|-|-|
|Terminal|匹配和消费一个 token 的代码|
| NonTerminal |调用这个规则的函数|
|`\|`|`if`或者`switch`语句|
|`*`或者`+`|`while`或者`for`循环|
|`?`|`if`语句|

### `parser` 类
每个语法规则就是`parser`类的一个方法：
```java
class Parser{
      private final List<Token> tokens;
      private int current = 0;
      Parser(List<Token> tokens){
            this.tokens = tokens;
      }
}
```
`parser`消费一个平坦的`token`输入序列。我们将`token`存入列表中并使用`current`指针指向下一个等待解析的`token`。

第一个`expression`代码如下：
```java
private Expr expression(){
      return equality();
}
```
解析语法规则的每个方法会产生那个规则的语法树并返回它给调用者。当规则的规则体包含一个 nonterminal (指向另一个规则的引用)我们就调用另一个规则的方法。

`equality`的规则如下：
```
equality → comparison(( "!="|"==") comparison)* ;
```
翻译过来的代码如下：
```java
private Expr equality(){
      Expr expr = comparison();

      while (match(BANG_EQUAL,EQUAL_EQUAL)){
            //如果match了,注意到match会消费一个token，因此
            //操作符应该取previous()
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr,operator,right);
      }
      
      return expr;
}
```
`equality`规则的第一个`comparison` nonterminal就翻译为方法中对`comparison()`的第一次调用。我们将它存放到临时变量中。然后，规则中的`(...)*`循环映射为`while`循环。我们需要知道什么时候退出这个循环。看下这个规则，我们必须要先匹配一个`!=`或者`==` `token`，才能继续循环，因此，如果没有找到其中一个，我们就以一系列的等值操作符结束。我们使用`match()`检查：
```java
//比较当前token是不是属于传入的指定TokenTypes之一
private boolean match(TokenType... types){
      for(TokenType type : types){
            if(check(type)){
                  advance();
                  return true;
            }
      }
      return false;
}
//比较当前的token是不是传入的指定类型
private boolean check(TokenType type){
      if(isAtEnd()) return false;
      return peek().type == type;
}

//advance会消费一个token并返回这个token
private Token advance(){
      if(!isAtEnd()) current++;
      return previous();
}

private boolean isAtEnd(){
      return peek().type == EOF;
}
//返回我们要消费的当前token
private Token peek(){
      return tokens.get(current);
}
//返回最近消费过的token
private Token previous(){
      return tokens.get(current - 1);
}
```
上面的代码就是我们解析架构所需要的代码的大部分了。如果我们在`equality()`的`while`循环内，我们就知道我们找到了`==`或者`!=`操作符，因为肯定是正在解析一个`equality`表达式。我们通过匹配的操作符token可以跟踪的我们的`equality`表达式的类型。然后再次调用`comparison()`解析右手的操作数。我们将操作符和它的两个操作数组合到新的`Expr.Binary`语法树节点，然后继续循环。我们将结果表达式存回到同一个`expr`局部变量中。随着我们拉开`equality`表达式序列，我们就创建了一个左关联的二元操作符节点的嵌套树。

![`equality`表达式序列解析](sequence.png)

解析`a == b == c == d == e`的过程中，每次迭代我们就会创建一个新的二元表达式，它使用前一个作为左操作数。一旦遇到一个非`equality`操作符，我们就从循环中退出。最后，它返回一个表达式。注意，如果解析器从没有遇到`equality`操作符，他就永远不会进入到循环中。此时，`equality()`方法就调用并返回`comparison()`。这样，这个方法就匹配上了`equality`操作符或者 _其他任何优先级更高的东西_。

接着处理`comparison`规则：
```
comparison → term (( ">"|">="|"<"|"<=")term)*;
```
翻译成代码：
```java
private Expr comparison(){
      Expr expr = term();
      while(match(GREATER,GREATER_EQUAL,LESS,LESS_EQUAL)){
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr,operator,right);
      }

      return expr;
}
```
`equality`和`comparison`语法规则结构类似，所以代码的结构也类似，唯一不同的地方就是匹配的操作符和产生操作数的方法不同(可以通过 lambda 表达式处理)。按照优先级，分别就是加减然后是乘除：
```java
private Expr term(){
      Expr expr = factor();
      while(match(MINUS,PLUS)){
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr,operator,right);
      }
      return expr;
}

private Expr factor(){
      Expr expr = unary();
      while(match(SLASH,STAR)){
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr,operator,right);
      }

      return expr;
}
```
接着是一元操作符：
```
unary → ("!"|"-") unary
      | primary;
```
代码如下：
```java
private Expr unary(){
      if(match(BANG,MINUS)){
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator,right);
      }
      return primary();
}
```
我们检查token是否是一元操作符，如果是，我们就获取这个token，递归地调用`unary()`解析操作数。将所有这些一起包裹到一个一元表达式语法树就成功了。否则就需要解析优先级最高的表达式了：
```
primary → NUMBER|STRING|"true"|"false"|"nil"|"(" expression ")";
```
解析的代码如下：
```java
private Expr primary(){
      if(match(FALSE)) return new Expr.Literal(false);
      if(match(TRUE)) return new Expr.Literal(true);
      if(match(NIL)) return new Expr.Literal(null);

      if(match(NUMBER,STRING)){
            return new Expr.Literal(previous().literal);
      }

      if(match(LEFT_PAREN)){
            Expr expr = expression();
            //消费"("和内部的表达式后，必须要消费")"，否则就是错误的
            consume(RIGHT_PAREN,"Expect ')' after expression");
            return new Expr.Grouping(expr);
      }
}
```

### 语法错误
解析器的两个任务：
1. 根据合法的 token 序列产生对应的语法树
2. 根据不合法的 token 序列，检测任意错误并告诉用户他们的错误

第二个工作依旧重要，在现代 IDEs 和编辑器中，解析器会时刻重新解析代码，通常是在用户还在编辑代码期间，这是为了做到语法高亮以及支持比如自动补全这样的功能。这意味着，如果解析器会一直遇到代码处于不完整，半错误的状态。解析器要负责将用户在没有意识到自己犯错的情况下引导刀正确的路径上。解析器报错的方式是语言用户接口很大的一部分。好的语法错误处理很难。由于代码并不是处于良好定义的状态，所以没有完美的方式可以知道用户想写什么。解析器没法读到你的想法。

对于进入到语法错误的解析器，有几点必须要做到：
* __检测和报告错误__ 这是必须的，不然无脑的将错误的语法树传给解释器，就会有不可知的情况
* __避免崩溃和假死__ 即使源文件不是合法的代码，但对于解析器依旧是合法的输入，因此，解析器不应该出现崩溃或者陷入无限循环，而用户也需要根据解析器来学习什么是允许的语法。

而一个优秀的解析器还需要：
* __快速__ 没有最快，只有更快
* __尽可能多的报告存在的不同错误__ 如果遇到一个错误就停下来，然后用户修复完一个再报一个的话，很讨厌。
* __最小化级联(cascaded)错误__ 一旦发现单个错误，解析器就不知道要怎么走下去了(毕竟已经是错误的语法了，解析器不知道要怎么执行下去了)。尽管他会尝试回到跟踪的上面并保持继续，但是它是不知道真正该怎么处理，可能会导致它产生一系列幽灵错误，这些错误不指向真的编码错误。当解决了第一个错误，这些幻象就都消失了，因为这些幻象其实是解析器的自己的困惑。应该尽量避免过多的级联错误。

解析器响应错误并继续查找后续错误的方式叫做 __错误恢复(error recovery)__。

#### Panic 模式错误恢复
__ Panic 模式__ 错误恢复技术是错误恢复技术中的标志技术。当解析器遇到错误时，他就进入到了错误模式。它知道至少有一个 token ，它在语法产生式的某个栈中间的当前状态不对。在它回到解析过程前，解析器需要让它的状态和即将到来的 tokens 序列对齐，这样下一个 token 才能匹配被解析的规则。这个过程叫做 __同步(synchronization)__。我们要在语法中选择一些规则标记同步点(synchronization point)。解析器会从任意嵌套的产生式中跳出，返回到这些规则，从而修复自己的解析状态。哪些位于这些被丢弃的 tokens 中的其他真实语法错误都不会报告，但是这也意味着由初始错误导致的任何弄混的级联错误都不会假性的报告出来，这是在少报级联错误和多报真实的错误之间的一个权衡。

语法中进行同步的传统地方是语句之间。我们目前还没有实现语句，因此这里不会真正的处理同步。只是先完成一些基本框架。

#### 进入 panic 模式
回到我们在解析括号表达式时，最后解析器调用`consume()`查找关闭的`)`。
```java
private Token consume(TokenType type,String message){
      if(check(type)) return advance();
      throw error(peek(),message);
}

private ParseError error(Token token,String message){
      Lox.error(token,message);
      return new ParseError();
}
```
而`Lox.error()`会报告具体的错误，并返回一个异常。
```java
static void error(Token token,String message){
      if(token.type == TokenType.EOF){
            report(token.line," at end",message);
      }else{
            report(token.line," at '" + token.lexeme + "'",message);
      }
}
```
`ParseError`继承了`RuntimeException`，我们用它作为哨兵，来unwind解析器。`error()`方法返回这个错误而不是抛出它，因为我们想要让在解析器里面的调用方法决定是否unwind。某些解析错误发生的地方，解析器可能不会进入到奇怪的状态中，这样我们就不需要做同步，如果是这样，我们就简单的报告一下错误，并继续前进。举个例子，Lox限制了函数参数数量，如果你传入了过多的参数，解析器只需要报告这个错误，然后继续解析额外的参数就好了，不需要进入panic模式。
>另一个处理常见语法错误的方式是 __错误生成式(error production)__。在语法中添加一个规则可以成功匹配 _错误的_ 语法。解析器安全的解析他然后将它报告为一个错误而不是生成一个语法树。比如，Lox不支持一元的`+`操作符，比如`+123`，Lox可以扩展规则为
>```
>unary → ("!"|"-"|"+") unary
>      | primary;
>```
>解析器可以正确的消费`+`，而不会进入panic模式或者奇怪的状态中。由于解析器开发人员知道什么代码是错的以及用户可能想要做的是什么，因此它们可以给出更加有用的错误帮助信息，帮助用户回到正确的位置。这使得错误生成式能很好的工作，成熟的解析器倾向于增加错误生成式，从而帮助用户修复常见的错误。

#### 同步一个递归下降解析器
在递归下降的解析器中，它的状态(即解析器正处于哪个规则的识别中间)没有显式存储在字段中。我们使用`Java`自己的调用栈跟踪解析器正在做什么。每个规则在被解析的中间都是栈上的一个调用帧。重置状态需要清理这些调用栈。在Java中，处理这个问题的一个自然方式就是异常。当我们要同步时，我们抛出一个`ParseError`对象。在更高层的方法(处理我们正要同步的语法规则的方法)中，我们捕获它。因为我们在语句边界处同步，我们就在这里捕获异常。异常捕获后，解析器就位于正确的状态了，剩下的就是同步tokens了。我们会丢弃下一个语句前的所有token，这个边界很容易找到，这也是我们选择它的一个主要原因。在分号后，我们很可能旧结束了一个语句。大多属语句是从一个关键字开始的，`for`，`if`，`return`，`var`等的。当下一个语句是这些中的任意一个，我们可能就开始了一个语句。
代码如下：
```java
private void synchronize(){
      advance();
      while(!isAtEnd()){
            if(previous().type == SEMICOLON) return;

            switch(peek().type){
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
```
>在`for`循环中，我们也可能遇到一个分号，它并没有结束一个语句。
我们丢弃所有tokens直到它认为遇到了一个语句边界。在捕获`ParseError`后，我们调用这个方法并期待回到同步中。当工作结束后，我们已经丢弃了的tokens是可能造成级联错误的tokens，然后我们从新的语句开始解析剩下的文件。

### 把解析器串起来
我们还有一个地方需要增加错误处理。解析器下降走过每个语法规则的解析方法，最终遇到`primary()`。如果无法命中任何一个情况，这意味着我们遇到了无法开始一个表达式的token。我们还需要处理这个错误。要在`primary()`方法中抛出一个异常。
```java
private Expr primary(){
      if(match(FALSE)) return new Expr.Literal(false);
      if(match(TRUE)) return new Expr.Literal(true);
      if(match(NIL)) return new Expr.Literal(null);

      if(match(NUMBER,STRING)){
            return new Expr.Literal(previous().literal);
      }

      if(match(LEFT_PAREN)){
            Expr expr = expression();
            //消费"("和内部的表达式后，必须要消费")"，否则就是错误的
            consume(RIGHT_PAREN,"Expect ')' after expression");
            return new Expr.Grouping(expr);
      }

      throw error(peek(),"Expect expression.");
}
```
然后就是定义一个初始化方法启动解析：
```java
Expr parse(){
      try{
            return expression();
      }catch(ParseError error){
            return null;
      }
}
```
当前`parse()`只处理单个表达式并返回，后面会增加对语句的处理。它还捕获了`ParseError`，毕竟语法错误恢复是解析器的工作，我们不希望`ParseError`异常逃逸到解释器其他地方。解析器承诺了不会因为无效的语法而崩溃或者假死，但是可以返回`null`。一旦解析器报告了错误，`hadError`就被设置了，后续阶段也就跳过了。
>当然可以设计一个语法比 Lox 复杂的多的语言，进而使递归下降解析变得不可能。当你需要向前看大量的 token 才能知道属于什么语法的时候，预测解析就很麻烦了。
>实践中，大多数语言都避免设计成那样。即使万一它们在某些语法上不是那样，依旧可以不用很痛苦的使用 hack 的方式处理它。如果你可以使用递归下降解析 C++ ，就可以解析任何语言。

### 设计笔记：逻辑 vs 历史
C语言将位操作`&`和`|`操作符的优先级放的比`==`低，大多数跟随C语言的语言也就这样设计了。但是，这目前被认为是一个错误，因为这意味着像是测试一个标志这样常见的操作需要括号：
```c
if(flags&FLAG_MASK == SOME_FLAG){}//wrong
if((flags&FLAG_MASK)== SOME_FLAG) {} //right
```
如果我们在设计一门新语言，那我们需要将位操作符优先级设置的比C高么？首先，我们几乎很少使用`==`表达式的结果作为位操作符的操作数。因此，如果让位操作绑定的紧一些，我们就不需要总是将位操作括起来。这样，用户就会假设优先级的选择逻辑上是最小化括号的使用，用户很可能认为这是对的。这种内在一致性使得语言易于学习，因为它的例外很少。一个更简单，更加合理的语言才有意义。

但是，很多用户是通过使用他们已经知道的概念来快速的理解我们语言的理念。如果我们的语言使用了和大多数语言一样的语法或者语义，那么用户需要学习的就更少了。这对语法很有帮助。利用用户已知的信息，是你可以用来缓解适应你语言的最重要的工具之一。 C 语言的位操作符优先级是一个显而易见的错误，但是数百万用户已经熟悉了这个错误。选择内在逻辑而忽略历史，还是选择跟随历史，这没有完美的答案，只有权衡。

### 笔记
实现了本章的`Parser`后，会解析`Scanner`产生的tokens。比如说，`1+2`这样的表达式生成Token列表是，`1`,`+`,`2`。`Parser`解析后，会产生一个`BinaryExpr`。注意，此处只会按照语法规则进行匹配。因此，如果代码是`1+12"a"`，`Scanner`产生的Token列表是,`1`,`+`,`12`,`"a"`。 按照语法规则，他会匹配`term`规则。但是，注意，`Parser`依旧会产生一个`BinaryExpr`，不过只包含`1`,`+`,`12`这三个Token。由于当前没有任何规则可以匹配`12`后面跟一个`"a"`这种现象，此时，`"a"`这个token被静默地消费了，但是不产生任何`Expr`。此章，对于这个表达式不会语法错误。