## 控制流
### 图灵机简介
为了解决第三次数学危机（著名的一个悖论就是罗素悖论），数学家决定从少量的公理，逻辑和集合论出发，构建没有问题的数学体系。这能够回答诸如“所有为真的语句都可以被证明么？”，“我们是否可以计算所有可以定义的函数？”，甚至是更一般的问题，“当我们称一个函数是可计算的，这意味着什么？”。数学家假设前两个答案是“对的”，但是，结果却是“错的”，而这两个问题有着深刻的联系，它涉及到大脑能够做什么以及整个宇宙如何工作。 Alan Turing 和 Alonzo Church 在证明前两个问题的结果是错的过程中，给最后一个问题设计了一个精确的答案--可计算函数的定义。他们分别设计了一个微小的系统，包含一个最小的机械集合（ minimum set of machinery ），这个系统依旧有足够的能力计算很大一类函数。这些函数都被认为是“可计算函数”。图灵的系统叫做 __图灵机（ turing machine ）__，Church 的叫做 __lambda演算（ lambda calculus ）__。这些依旧被广泛的作为计算模型的基础，很多现代函数式编程语言核心就是 lambda 演算。
>他们通过证明返回给定语句真值的函数不是可计算的函数证明了第一个问题是错的。
>图灵称他的发明是“ a-machines ”，表示自动（ automatic ）。后人称之为图灵机

图灵机和 lambda 演算是[等价的](https://en.wikipedia.org/wiki/Church%E2%80%93Turing_thesis)，事实上任何有着某种最低表达能力的编程语言足够计算 _任何_ 可计算函数的。可以通过使用你的语言编写图灵机的模拟器证明这一点。由于图灵证明了图灵机可以计算任何可计算函数，因此，你的程序语言也可以。所要做的就只是将这个函数翻译到图灵机中，然后在你的模拟器上运行它。如果一个语言足够表达这个，就认为是 __图灵完备的（ Turing-complete ）__。图灵机相当简单，基本上你的语言只需要算术，一点控制流，以及可以分配并使用任意数量（理论上）内存的能力。
>第三点几乎已经拥有了。你可以创建并拼接任意长的字符串，所有可以存储无界的内醋呢，但是没有任何办法访问这个字符串的一部分。
### 条件执行
控制流大致可以分为两类：
* __条件__ 或 __分支控制流__ 用于不执行某些代码。
* __循环控制流__ 用于多次执行一段代码。通常还包含一些条件逻辑判断何时停止循环。
类 C 的语言有两个主要的条件执行功能。一个是`if`语句，一个是条件操作符（`?:`）。前者条件执行语句，后者条件执行表达式。 Lox 不包含条件操作符。`if`语句的语法规则如下，有一个条件表达式，以及当条件为真时执行的语句。还有一个可选的`else`关键字以及当条件为假时执行的语句。：
```
statement → exprStmt
          | printStmt
          ｜ ifStmt
          | block;
ifStmt     → "if" "(" expression ")" statment
             ("else" statement )? ;
```
解析`if`语句的代码如下：
```Java
//statement()
private Stmt statement(){
    if(match(TokenType.IF)){
        return ifStatement();
    }
    ...
}
private Stmt ifStatement(){
    consume(TokenType.LEFT_PAREN,"Expect '(' after 'if'.");
    Expr condition = expression();
    consume(TokenType.RIGHT_PAREN,"Expect ')' after 'if' condition.");
    Stmt thenBranch = statement();
    Stmt elseBranch = null;
    if(match(TokenType.ElSE)){
        elseBranch = statement();
    }
    return new Stmt.If(condition,thenBranch,elseBranch);
}
```
>条件表达式周围的括号其实起作用的知识右括号，我们需要在语句和条件表达式之间存在一个分隔符，不然 parser 不清楚哪里是条件表达式的结尾。所以我们使用一对括号包裹条件表达式，这样右括号作为分隔符而左括号为了保持平衡。其他语言使用类似`then`这样的关键字作为分隔符，因此在条件表达式附近就没有必要使用括号了。而 Go 和 Swift 要求这里的语句必须是块，这使得它们可以用语句开头的大括号作为分隔符，因此不需要括号。

这里可选的`else`引入了歧义，如果有嵌套的`if`那么，`else`会匹配谁，这会影响代码的执行。考虑下面的代码，如果`else`和第一个`if`语句匹配。那么当`first`为假就会调用`whenFalse()`不关心`second`的值。如果`else`和第二个`if`语句匹配。`whenFalse()`只有在`first`为真，`second`的假才会执行。由于`else`是可选的，`if`又没有现实的分隔符标示结尾，因此语法就会在这样嵌套`if`时出现歧义。这类语法陷阱叫做 [悬空 else ](https://en.wikipedia.org/wiki/Dangling_else) 问题。
```Java
if(first) if(second) whenTrue();else whenFalse();
//第一种可能
if(first)
    if(second)
         whenTrue();
else
    whenFalse();
//第二种可能
if(first)
    if(second)
        whenTrue();
    else
        whenFalse();
//注意，解析器会忽略所有空格，上面仅仅是可能的解析结果。
```
虽然可以通过定义一个上下文无关语法直接避免悬空 else 问题。但是这会带来很多工作量，需要把大部分语法规则分成一对，一个用于允许`if`和`else`一起，一个不允许。大多数语言和解析器都以一种特别方式避免这一问题，不管语言具体使用什么方式解决问题，它们总是选择同样的解析方式--`else`是跟前面距离它最近的`if`绑定。

上面的`ifStatement()`本身已经完成了这个工作，因为它在返回前会先查找`else`，对嵌套序列的最内层调用会在返回到外层`if`语句前宣称`else`子句是自己的。解释的逻辑如下：
```java
// interpreter.java
@Override
public Void visitIfStmt(Stmt.if stmt){
    if(isTruthy(evaluate(stmt.condition))){
        execute(stmt.thenBranch);
    }else if(stmt.elseBranch != null){
        execute(stmt.elseBranch);
    }
    return null;
}
```
解释逻辑就是对 Java 执行`if` 的简单包装。估值条件，为真，执行 then 分支，否则执行 else 分支。在已经实现的逻辑中，这段代码和我们解释器处理其他语法不同的是这里 Java 的`if`语句，大多数其他语法树总是会估值自己的子树，而这里我们可能不会估值 then 或者 else 语句。如果二者之中有谁有副作用，这种选择不估值它的逻辑就是用户可见的。
### 逻辑操作
即使我们没有三院操作符，但是`and`和`or`这两个逻辑操作符也是控制流构造。它们是 __短路的（ short-circuit ）__，如果在估值左侧操作数之后我们就知道整个逻辑表达式的结果了，就不会估值右操作数。比如：
```
false and sideEffect()
```
由于`and`必须是两个操作数都是真的情况才会得到真，因此在估值左侧的`false`操作数后，我们就知道这个表达式的结果是`false`，因此就跳过`sideEffect()`不估值。这就是在实现二元操作符的时候没有实现逻辑操作符的原因。这两个操作符的优先级很低，类似于 C 的`||`和`&&`，而`or`的优先级低于`and`，因此，语法规则如下：
```
expression → assignment;
assignment → IDENTIFIER "=" assignment
           | logic_or;
logic_or   → logic_and ( "or" logic_and )*;
logic_and  → equality ( "and" equality )*;
```
现在`assignment`会落回到`logic_or`，`logic_or`和`logic_and`和其他二元操作符类似。而`logic_and`会调用`equality`处理它的操作数，这就回到了剩下的表达式规则。
> _语法_ 不关心短路，这是语义关心的
不使用`Expr.Binary`作为逻辑操作符的 AST ，是因为需要在`visitBinaryExpr()`判断操作符是不是逻辑操作符，如果是就需要有另外的代码路径处理短路。因此，引入一个新的类处理逻辑操作符。
```Java
//GenerateAst.java
   "Literal  : Object value",
   "Logical  : Expr left, Token operator, Expr right",
   "Unary    : Token operator, Expr right",
```
代码改造为：
```Java
//Parser.java,生成AST
Expr expr = or();
private Expr or(){
    Expr expr = and();
    while(match(OR)){
        Token operator = previous();
        Expr right = and();
        expr = new Expr.logical(expr,operator,right);
    }
    return expr;
}
private Expr and(){
    Expr expr = equality();
    while(match(AND)){
        Token operator = previous();
        Expr right = equality();
        expr = new Expr.Logical(expr,operator,right);
    }
    return expr;
}
//interpreter.java
public Object visitLogicalExpr(Expr.Logical expr){
    Object left = evaluate(expr.left);
    if(expr.operator.type == TokenType.OR){
        if(isTruthy(left)) return left;
    }else{
        if(!isTruthy(left))return left;
    }
    return evaluate(expr.right);
}
```
这里，和`visitBinaryExpr()`不同的是，我们先估值左操作数，然后检查值是否需要短路，如果不需要，只有这种情况，才会估值右操作数。这里，由于 Lox 是动态类型，所以操作数可以是任何类型，然后使用真性（ truthiness ）判断操作数代表什么。对于结果也是类似的处理逻辑，不是真的返回`true`和`false`，逻辑操作服符只保证返回的值有合适的真性，也就是操作数的结果自身。
```
print "hi" or 2 //"hi"，短路并返回hi
print nil or "yes" //"yes" 返回yes
```
### while 循环
先处理`while`循环：
```
statement  → exprStmt
           | ifStmt
           | printStmt
           | whileStmt
           | block ;

whileStmt      → "while" "(" expression ")" statement ;
```
语法上消耗一个`while`关键字，跟着一个括号条件表达式然后是语句体，对应的语法树节点：
```Java
//generateAst.java
"Print      : Expr expression",
"Var        : Token name, Expr initializer",
"While      : Expr condition, Stmt body"
```
这里可以看到，节点的`condition`是表达式，而`body`是语句。在`Parser`中，需要先匹配关键字，然后解析整个语句：
```Java
//parser.java statement方法
if(match(WHILE)) return whileStatement();
//parser.java
private Stmt whileStatement(){
    consume(LEFT_PAREN,"Expect '(' after 'while'.");
    Expr condition = expression();
    consume(RIGHT_PAREN,"Expect ')' after condition.");
    Stmt body = statement();
    return new Stmt.While(condition,body);
}
//interpreter.java
@Override
public Void visitWhileStmt(Stmt.While stmt){
    while(isTruthy(evaluate(stmt.condition))){
        execute(stmt.body);
    }
    return null;
}
```
### for 循环
`for`循环的的语法如下：
```
statement  → exprStmt
           | forStmt
           | ifStmt
           | printStmt
           | whileStmt
           | block ;

forStmt    → "for" "(" (varDecl | exprStmt | ";")
              expression? ";"
              expression? ")" statement;
```
>大多数现代语言会有高阶的循环语句，可以迭代循环任意用户定义的序列。 C# 有`foreach`， Java 有“增强的 for ”， C++ 有基于范围（ range-based ）的`for`语句。这比 C 的`for`语句更加清晰，隐含了调用了被循环对象支持的迭代协议。不过由于目前 Lox 还没有实现对象和方法，就没有办法定义迭代协议（ iteration protocol ）

`for`语句的括号中又逗号分隔成三个子句：
1. 第一个子句是 _初始化器（ initializer ）_，只会在最开始执行一次。可以是表达式或者是变量声明。如果是变量声明，变量的作用域只在整个`for`循环：后面两个子句和循环体。
2. 接下来是 _条件（ condition ）_，用来控制退出循环。在每次迭代开始执行，包括第一次。如果结果为真，就执行循环体，否则退出循环。
3. 最后一个子句是 _增量（ increment ）_。它是每次循环结束后执行工作的任意表达式。一般来说，包含副作用才有用。实践上通常会对变量做增量操作。
任何一个子句都可以被忽略。括号后是一个语句体，通常是块结构。

#### 解语法糖（ Desugaring ）
如果`for`循环不支持初始化器语句，可以讲初始化表达式放在`for`循环外，如果不支持增量子句，可以讲增量语句放在循环体末尾。换句话说，没有特别的理由需要`for`循环，它仅仅让某些常见的代码模式更加易写。这种类型的功能叫做 __语法糖（ syntactic sugar ）__。下面给出了改写的`for`循环示例：
```
for(var i = 0; i < 10; i++>){
    print i;
}
//desugar
{
    var i = 0;
    while( i < 10 ){
        print i;
        i = i + 1;
    }
}
```
上面两段代码是等价的，这样的语法糖有助于增加语言的便捷增加创造力。 __ desugar __ 描述了一个过程，解释器前端接收了使用语法糖的代码并将它转换为解释器后端已经知晓如何执行的更基本的形式。这里，我们的解释器会将`for`循环转换为解释器已经处理过的`while`循环和其他语句。
```Java
//parser.java
import java.util.Arrays;
//parser中的statement()方法中
if(match(FOR)) return forStatement();
//parser.java
private Stmt forStatement(){
    consume(LEFT_PAREN,"Expect '(' after 'for'.");
    Stmt initializer;
    //如果"("后直接是";"，那么初始化器就被忽略了，
    //否则，判断有没有"var"，如果有，表示是一个变量声明
    //否则，肯定是一个表达式，就将其包装为一个表达式语句
    //这样，就可以确保initializer为Stmt类型
    if(match(SEMICOLON)){
        initializer = null;
    }else if (match(VAR)){
        initializer = varDeclaration();
    }else{
        initializer = expressionStatement();
    }
    //再次根据下一个字符是不是";"判断是否忽略了条件子句
    //如果不是，条件语句是一个表达式
    Expr condition = null;
    if(!check(SEMICOLON)){
        condition = expression();
    }
    consume(SEMICOLON,"Expect ';' after loop condition");
    //判断下一个字符是不是")"，如果不是，解析增量子句
    Expr increment = null;
    if(!check(RIGHT_PAREN)){
        increment = expression();
    }
    consume(RIGHT_PAREN,"Expect ')' after for clauses.");
    //解析循环体
    Stmt body = statement();
    //开始desugar
    if(increment != null){
        body = new Stmt.Block(
            Arrays.asList(
                body,
                new Stmt.Expression(increment);
            )
        );
    }
    if(condition == null) condition = new Expr.Literal(true);
    body = new Stmt.While(condition,body);
    if(initializer != null){
        body = new Stmt.Block(Arrays.asList(initializer,body));
    }
    return body;
}
```
在解析完初始化器，条件子句，增量子句和循环体后，我们有了足够的 Java 变量将`for`循环解语法糖为`while`语句。从后向前处理代码会简单些，所以，代码首先处理增量子句。将增加子句加到循环体语句之后，组成新的语句。然后跟条件子句一起构造`while`语句，如果条件语句没有，就构造一个字面量语句，值为`true`，创建一个无限循环。最后，如果初始化器不为空，它需要在整个循环执行先运行一次，然后将初始化器和`while`语句组合一起构造出一个新的块语句，最后的的结果就是将`for`解语法糖为`while`的结果了。到此，我们的解释器就可以解释执行 C 风格的`for`循环，而整个过程没有修改任何`Interpreter`类，因为我们解语法糖得到的结果是`Interpreter`已经知道如何访问的了。

现在， Lox 已经支持了控制流了。
> #### 设计笔记：关于语法糖
> 在语言设计上，是一点语法糖都不增加，每个语义操作都映射到单个语法但单元上，还会有大量的语法糖，每种行为都可以数十种表达式是一种设计品味。目前成功的语言在语法糖设计上什么选择都有。
> Lisp ， Forth 和 Smalltalk 这样最小化语法的语言基本没有语法糖。这些语言的设计者信奉 _语言_ 不需要语法糖。相反，语言提供的最小化的语法和语义足够强大，让库代码的表达能力好像是语言自身的一部分一样。
> C ， Lua 和 Go 与它们接近。他们的目标是简单清晰而不是最小化。像是 Go 这种语言，它不仅限制语法糖，还约束了自身不像上一分类的语言那样的语法扩展类型。它们希望语法不影响语义，因此专注于保持语法和库简单。相对于漂亮，代码更应该显然。
> Java , C# 和 Python 位于中间，然后是 Ruby ， C++ ， Perl 和 D 。语言提供的语法糖多少某种程度上也跟语言的年龄有关。在后面的版本中增加语法糖要更简单些。新的语法更受欢迎，而且相比于篡改语义，它们更不太可能破坏已有的代码。一旦添加了新语法，就不能再移除它，因此语言倾向于增加甜度。从头开始创建新语言的好处就是可以移除这些语法糖。
> PL 设计者不认可语法糖，这也是有原因的。设计糟糕的无用语法会增加认知负担，却没有增加足够的表达能力承载这种负担。总是存在给语言增加新功能的压力，需要纪律和对简洁的关注以避免膨胀。对于增加语法应当保持吝啬，因为一旦添加了新语法，就会陷入进去。
> 同时，大多数现代语言都有足够复杂的语法，程序员会花费很多时间选择语言，好的语法可以提升它们的工作效率和舒适度。
> 这就是平衡的艺术，以及对语言的品味。
