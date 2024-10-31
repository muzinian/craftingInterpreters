## 语句和状态（ Statements and State ）
目前为止，现在的解释器还没办法将某些数据或者函数绑定（ bind ）到一个名字上，这样就没有办法组合成一个软件。为了支持绑定，解释器需要内部状态。状态和语句是一起的，根据定义，语句不会估值，而是产生 __副作用（ side effect ）__。它们可能会产生可见的输出或者修改解释器内的某些状态，解释器在后续操作能够感知到修改。后者就让语句非常适合定义变量或者其他命名实体。

本章会定义产生输出（ `print` ）和创建状态（ `var` ）的语句，还会处理访问和赋值变量的表达式，并增加块（ block ）和局部作用域（ local scope ）。
### 语句（ statement ）
从最简单的两个开始：
1. __表达式语句（ expression statement ）__ 允许用户在需要语句的地方放置表达式。它们的存在是用来估值会产生副作用的表达式。只要你看到一个方法/函数调用后面跟着一个`;`，就是一个表达式语句
2. __`print`语句__ 估值一个表达式并将结果输出给用户。大部分语言会把这个打印功能作为库函数。但是这本书希望每章介绍解释器的内容都是可用的，所以会直接在解释器内实现这个功能。而不用等到实现了定义和调用函数之后的机制才能观察到副作用。
>BASIC 和 Python 也有`print`语句，但是 Python 在3.0之后取消了。

新的句法（ syntax ）需要新的语法（ grammar ）规则。下面是新的语法规则：
```
program   → statement* EOF;

statement → exprStmt
          | printStmt;

exprStmt  → expression ";";
printStmt → "print" expression ";";
```
第一个规则是`program`，它是 Lox 的语法规则的开端，代表了一个完整的 Lox 脚本或者 REPL 条目。一个程序就是一个语句列表加一个“文件结尾”的 token （ EOF ）。这个强制结束标志是为了保证 parser 消费整个输入并且不会静默地忽略脚本最后错误的未消费 tokens 。
#### 语句语法树
在语法规则中，没有地方同时允许语句和表达式。由于这两个句法是不相交的，所以设计上我们不要两者共同继承某一个基类。将语句和表达式分成两个独立的类体系确保了 Java 编译器可以帮助我们找到简单的类型错误问题。我们在代码生成工具类传入一个如下参数：
```Java
defineAst(outputDir, "Stmt", Arrays.asList(
    "Expression:Expr expression",
    "Print: Expr exprssion"
));
```
#### 解析语法树
首先需要修改上一章的`Parser`类的`parse`方法：
```Java
//parser.java
List<Stmt> parse(){
    List<Stmt> statements = new ArrayList<>();
    while(!isAtEnd()){
        statements.add(statement());
    }
    return statements;
}
```
`parse`会尽可能多的解析语句直到结尾。这是按照递归下降方式解析`program`语法规则的直接翻译。解析`statement`语法在遇到`print` 的 token 时将其当作`print`语句解析，如果下一个 token 不是认识的其他类型语句，就假设它是表达式语句，这是在解析语句时典型的最终匹配（ final fallthrough ）情况，因为很难从第一个 token 中识别表达式。`print`和`expressionStatement`都有自己的方法，代码如下：
```Java
private Stmt statement(){
    if(match(PRINT)) return printStatement();
    return expressionStatement();
}
private Stmt printStatement(){
    //由于我们已经匹配并消费了print这个token，所以这里就不需要再次这么做了，
    //直接解析后续表达式并消费终止的分号并返回语法树
    Expr value = expression();
    consume(SEMICOLON,"Expect ';' after value.");
    return new Stmt.Print(value);
}

private Stmt expressionStatement(){
    Expr expr = expression();
    consume(SEMICOLON,"Expect ';' after value.");
    return new Stmt.Expression(expr);
}
```
#### 执行语句
我们使用访问者模式解释语句，由于语句是单独类，有自己的访问者接口，所以需要修改`Interpreter`类实现这个接口。同时，还需要修改`interpret`方法，让它可以处理语句列表：
```Java
//interpreter.java
class Interpreter implements Expr.Visitor<Object>,Stmt.Visitor<Void>{
    //...省略的代码
    //估值表达式语句的表达式，丢弃结果，返回null
    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt){
        evaluate(stmt.expression);
        return null;
    }
    //估值表达式语句的表达式，打印结果，返回null
    @Override
    public void visitPrintStmt(Stmt.Print stmt){
        Object value = evaluate(stmt.expression);
        System.out.println(stringfy(value));
        return null;
    }

    void interpret(List<Stmt> statements){
        try{
            for(Stmt stmt:statements){
                execute(stmt);
            }
        }catch(RuntimeError error){
            Lox.runtimeError(error);
        }
    }
    private void execute(Stmt stmt){
        stmt.accept(this);
    }
}
```
### 全局变量（ global variables ）
对于变量，我们需要两个构造：
1. __变量声明（ variable declaration ）__ 语句给整个程序引入新的变量。我们称这给这个名字建立了新的绑定
2. __变量表达式（ variable expression ）__ 访问这个绑定。
```
var beverage = "espresso";
print beverage;
```
#### 变量句法
变量语句不同于其他语句，语法规则限制某些语句允许出现的地方。在控制流语句的子句中，比如`if` 及其 `else` 分支，都是单个的语句。如果子句不允许是变量声明，就没有问题，但是如果是，可能就会有问题，考虑下面的代码：
```
if(monday) print "aaaa";
if(monday) var x = 1;
```
对于第二个，如果我们允许在这里出现变量声明，那么这个变量的作用域范围在哪里？它的作用域在`if`之后还存在么？如果`if`的判断为`false`，它的值是多少？当其他日期的时候，这个变量存在么？因此，类 C 语言都禁止在控制流子句中出现变量声明语句。这里，就好像语句存在两个级别，某些地方-比如在块内或者顶层位置-允许所有类型的语句，包括声明，其他地方只允许那些不会声明名字的更高级别的语句。因此，语法规则如下：
>在这里，块语句的作用就跟表达式中小括号的作用类似的。块语句自身在更高的优先级中因此，可以用在任何地方，但是它内部可以包含低级别的语句。
```
program     → declaration* EOF;
declaration → varDecl
            | statement;
statement   → exprStmt
            | printStmt;
varDecl     → "var" IDENTIFIER ( "=" expression)? ";";
primary     → "true"|"false"|"nil"|NUMBER|STRING|"(" expression ")"|IDENTIFIER;
```
声明语句有了自己的语法规则`declaration`，目前只有变量声明，后序会包含函数和类，而允许声明语句的地方也允许其他语句，因此它的最后分支就是普通语句。而`program`也需要更新一下。`varDecl`规则以`var`开头，然后是一个标识符 token 作为被声明变量的名字，以及可选的初始化器（ initializer ）表达式和一个分号。在`primary`中增加了`IDENTIFIER`的 token 。

#### 解析变量
根据语法规则修改`parser`类的`parse`方法，然后根据语法规则，实现变量的解析方法。整个`declaration`方法会在解析脚本或者块的过程中重复的调用，因此它是处理第七章我们提到了错误恢复机制的好地方。在这里，我们将会在`parser`进入 panic 的时候进行同步。 因此`varDeclaration`按照语法规则递归下降解析变量声明语句。当遇到`=` token 时，就知道是一个初始化器表达式，然后解析它，否则就设置初始化器为`null`。最后，在`primary`中处理标识符的 token 解析。
```Java
//parser.java
public List<Stmt> parse(){
     List<Stmt> statements = new ArrayList<>();
     while(!isAtEnd()){
        statements.add(declaration());
    }
     return statements;
}
private Stmt declaration(){
    try{
        //匹配到了var关键字，就是变量声明
        if(match(TokenType.VAR)) return varDeclaration();
        //statement是fall through的，它会在没有匹配其他语句的情况下按照解析表达式语句的方式解析
        //如果表达式报告了她无法解析的表达式错误，整个调用链确保了如果解析不了一个语句就会报告错误
        return statement();
    }catch(ParseError error){
        //当发生异常，就在这里执行同步，这样就可以跳过有问题的声明语句，执行下一个
        synchronize();
        return null;
    }
}

private Stmt varDeclaration(){
    Token name = consume(IDENTIFIER,"Expect variable name.");
    Expr initializer = null;
    if(match(TokenType.EQUAL)){
        initializer = expression();
    }
    consume(SEMICOLON,"Expect ';' after variable declaration.");
    return new Stmt.Var(name,initializer);
}

private Expr primary() {
    ...
    if (match(TokenType.NUMBER, TokenType.STRING)) {
        return new Expr.Literal(previous().getLiteral());
    }

    if(match(TokenType.IDENTIFIER)){
        return new Expr.Variable(previous());
    }

    if (match(TokenType.LEFT_PAREN)) {
        ...
    }
}
```
### 环境
存储变量名字的地方叫做 __环境（ environment ）__。

Lox 允许对同一个变量重复定义，虽然用户重复定义已存在的变量很大可能是一个用户侧 bug ，但是如果将重复定义变量认为是错误，可能会让 REPL 变得困难。尽管我们可以做到在 REPL 中允许重新定义而在脚本中不允许，但这个可能会导致需要学习两套规范并使得从 REPL 迁移很困难（ Scheme 允许重复定义，书籍作者对于变量和作用域的规则遵循 Scheme ）。
```
var a  = "before";
print a;//before
var a  = "after";
print a;//after
```
当查询变量名的时候，也会出现语义问题，如果变量不存在，应该怎么做：
1. 静态的语法错误；
2. 运行时错误；
3. 允许并返回一个默认值如`nil`；
第三个选项过于宽松。而第一个选项虽然能及早检测错误，但是可能会让递归定义变得麻烦。麻烦在于，_使用 （ using ）_ 一个变量和 _引用（ referring ）_ 一个变量并不同。我们可以在一段代码中引用一个变量而不立即估值这个变量，只要这段代码被包裹在一个函数内部。如果选择实现第一个，即声明一个变量前 _提及（ mention ）_ 这个变量，就会让定义相互递归函数变得麻烦。对于自递归（自己调用自己）函数我们可以在处理函数体前声明这个函数名字，但是对于相互递归函数，就无法做到：
```
fun isOdd(a){
    if(n==0)return false;
    return isEven(n-1);
}
fun isEven(a){
    if(n==0)return true;
    return isOdd(n-1);
}
```
这个例子中，无论我们怎么交换着两个函数的定义，都无法解决在处理对应函数体中，另一个函数还没有定义的情况。因此， Lox 选择实现第二个，将错误延迟到运行时处理，只要不对比变量估值，就允许在变量定义前引用这个变量。
>某些静态类型语言，比如 Java 和 C# ，通过规定程序的顶层不是指令语句序列来解决这个问题。相反，一个程序是声明的集合，他们都同时出现。它们的实现先声明所有的名字，然后再查看函数的函数体。而对于 C 这种老的语言，由于它们发明的时候计算资源很受限，所以它要求显式地 _前向声明（ forward declaration ）_ 去在完整的定义个一个名字前声明它，这样就可以在一趟遍历文本就能编译代码，而这样编译器就不能在处理函数体前首先收集所有声明。

#### 解释全局变量
我们在解释器类中定义个一个`environment`变量，在处理定义语句的时候，根据情况，如果变量有初始化器表达式，就估值这个表达式，然后设置变量的值，如果没有，就设置为`nil`。当估值变量表达式的时候，就从`environment`中获取对变量的值。
```Java
//Environment.java
public class Environment{
    private final Map<String,Object> values = new HashMap<>();
    public void define(String key,Object value){
        values.put(key,value);
    }
    public Object get(Token name ){
        if(values.containsKey(name.lexeme)){
            return get(name.lexeme);
        }
        throw new RuntimeError(name,"Undefined variable '" + name.lexeme + "'.");
    }
}
//Interpreter.java
private Environment environment = new Environment();

@Override
public Void visitVarStmt(Stmt.Var stmt){
    Object value = null;
    if(stmt.initializer != null){
        value = evaluate(stmt.initializer);
    }
    environment.define(stmt.name.lexeme,value);
    return null;
}

@Override
public Object visitVariableExpr(Expr.Variable expr){
    return environment.get(expr.name);
}
```
### 赋值
很多语言认为赋值--也叫做 __mutate__ --或者说产生副作用的代码不好， Haskell 不允许对变量重新赋值， SML 只允许可变的引用和数组--变量不能重新赋值。 Rust 要求显式地使用`mut`修改符声明一个变量可以重新赋值。考虑 Lox 是一个指令式语言，允许对变量赋值或者有副作用没有问题。
#### 赋值语法
赋值使用`=`，在 Lox 中，他跟很多继承自 C 的语言一样，赋值是一个表达式而不是语句，同样，它的优先级是最低的。因此，它的语法规则如下：
```
expression → assignment;
assignment → IDENTIFIER "=" assignment
           | equality;
```
赋值要么是一个标识符后跟一个`=`和一个值的表达式，要么是`equality`（因此也是其他的）表达式。前瞻一个 token 的递归下降解析器除非已经解析完了左手边的标识符遇到`=`的时候，才能分辨出来正在解析赋值表达式。这跟解析像是`+`操作符是不一样的。虽然在解析`+`的时候，我们也是在解析完做操作数后，遇到了`+`才知道是解析加法表达式，但是赋值表达式的左手侧并不是一个需要估值的表达式。它像是一个伪表达式，能估值出一个你可以赋值的“东西”。比如：
```
var a = "before";
a = "after";
```
对于第二行，我们并不会估值`a`（如果估值，会返回`before`），我们评估了变量`a`引用的是什么，这样我们就知道将右手侧表达式值存放在哪里。这两个构造的[经典名字](https://en.wikipedia.org/wiki/Value_(computer_science)#lrvalue)是 __l-value__ 和 __r-value__ 。目前所有的表达式都是 r-values ， 一个 l-value 会“估值”出一个用户可以赋值的存储位置。语法树需要能反映出来一个 l-value 不会想普通表达式那样被估值。因此`Expr.Assign`节点需要一个 `Token`而不是`Expr`给左手侧。而对于一个复杂的 l-value ，可能会出现很多 token 。
```
makeList().head.next = node;
```
>由于字段赋值的接收者可以是任意表达式，而一个表达式可以是任意长的，因此在找到一个`=`前可能需要前瞻无数多个 token 。
因此，我们解析赋值的代码需要一个特殊的技巧：
```Java
//parser.java
private Expr assignment(){
    Expr expr = equality();
    if(match(TokenType.EQUAL)){
        Token equals = previous();
        Expr value = assignment();

        if(expr instanceof Expr.Variable){
            Token name = ((Expr.Variable)expr).name;
            return new Expr.Assign(name, value);
        }
        error(equals,"Invalid assignment target.");
    }
    return expr;
}
```
大部分代码跟解析`+`一样，解析左边的表达式（可能是任何更高优先级的表达式），如果我们遇到了一个`=`，我们就解析右边的表达式，然后将整个包裹起来作为一个赋值表达式树节点。跟二元操作符有点不同地方是不会为了构建一个相同操作符的序列而循环，由于赋值是右结合的，我们递归调用`assignment()`解析右手侧表达式。而特殊的技巧是，在创建赋值表达式节点前，我们看下左手边表达式的类型，看看是什么类型的赋值表达式目标。我们转换 r-value 表达式节点为一个 l-value 表达式。
>如果左手侧不是一个合法的赋值表达式目标，我们就报告一个错误，而不抛出一个异常。这是因为解析器不出于一个困惑的状态需要进入到 panic 模式并同步。
这个转换可以工作的原因是因为每个合法的赋值目标作为一个普通表达式也是合法的语法。比如：
```
newPoint(x+2,0).y = 3;
```
这个例子中，赋值左侧依旧可以作为一个合法表达式工作：
```
newPoint(x+2,0).y;
```
这样，我们就可以按照解析表达式的方式解析左手侧，然后在这之后产生一个语法树将它转换为赋值目标。如果不是一个合法的赋值目标，就遇到了语法错误，比如`a+b=c;`。
>即使赋值目标不是合法的，也可以使用这个技巧。定义一个 __cover grammar__（一个宽松的语法，接收所有合法的表达式和赋值目标语法），当你遇到了`=`，如果左手侧并不在一个合法的赋值目标语法中。相反，如果你没有遇到`=`，如果左手侧不是一个合法的表达式就报错。
>在解析的那一章，强调了需要在语法树中表示括号表达式，因为后续需要。这里，就是需要的原因。因为需要区分
>```
>a = 3 ;//ok
>(a) = 3; //Error.
>```
这个技巧最终的结果是赋值表达式树节点知道它正在给谁赋值以及被赋值的值的子表达式树。而这一切只需要前瞻一个 token 并且没有回溯。

#### 赋值语义
解释器需要增加新的访问者方法处理新的语法节点，它类似于变量声明，估值右侧得到值，存储到命名的变量中。不过赋值和定义的一个关键不同是，赋值不允许创建 _新_ 变量。这意味着，如果名字不存在于环境中，就报错。最后，这个访问方法会返回右侧估值的结果，这是因为赋值是一个可以嵌套在其他表达式中的表达式。
```
var a = 1;
print a = 2;
```
```Java
//Interpreter.java
@Override
public Object visitAssignExpr(Expr.Assign expr){
    Object value = evaluate(expr.value);
    environment.assign(expr.name,value);
    return value;
}
//Environment.java
void assign(TOken name,Object value){
    if(values.containsKey(name.lexeme)){
        values.put(name.lexeme,value);
        return;
    }
    throw new RuntimeError(name,"Undefined variable '" + name.lexeme + "'.");

}
```
>Lox 不同于 Python 和 Ruby ，不支持隐式[变量声明](https://craftinginterpreters.com/statements-and-state.html#design-note)（ implicit variable declaration ）。

### 作用域
__作用域（ scope ）__ 定义了名字映射到某个实体的区域。多个作用域使得同一个名字在不同的上下文中指代不用的东西。__词法作用域（ lexical scope ）__，也叫 __静态作用域（ static scope ）__，是一种类型的作用域，这种作用域可以通过程序文本自身就确定作用域开始和结束的位置。大多数现代语言以及现在实现的 Lox 都是词法作用域。例如：
```
{
    var a = "first";
    print a;
}
{
    var a = "second";
    print a;
}
```
只通过阅读代码就可以知道在这两个块中`print`语句里`a`指代的对象。相反，动态作用域 __（ dynamic scope ）__ 只有执行了代码才能知道一个名字指代的具体事物。 Lox 的变量不是动态作用域的，但是一个对象的方法和属性是动态作用域的。例如：
```
class Saxophone{
    play(){
        print "Careless Whisper";
    }
}
class GolfClub{
    play(){
        print "Fore!";
    }
}
fun playIt(thing){
    thing.play();
}
```
只通过阅读代码，我们没有办法知道`playIt`中`thing.play()`会调用的是`Saxophone`还是`GolfClub`的`play`，这要看传入进去的参数是什么，而这只有等到运行时才能知道。作用域和环境息息相关，前者是理论概念，后者是实现它的机制。随着我们的解释器解释执行代码，影响作用域的语法树节点会修改环境。 Lox 和其他类 C 语言使用块控制作用域。这也是为什么称之为 __块作用域(block scope)__。
>“ lexical ”这个词来自于希腊单词“ lexikos ”，意味着“与文字有关的”。当在编程语言中使用这个词的时候，它通常意味着不需要执行代码，只从源代码自身就可以看出事物的含义。 ALGOL 最先引入词法作用域。更早的语言通常都是动态作用域的。那个时候的计算机科学家认为动态作用域执行起来更快，但是根据早期的 Scheme 专家的结论，结果恰恰相反。动态作用域还存在， Emacs Lisp 默认对变量是动态作用域的。 Clojure 的 [binding](http://clojuredocs.org/clojure.core/binding) 宏也提供了这个能力。 JavaScript 的 [with语句](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Statements/with) 将一个对象的属性转换为动态作用的变量。
#### 嵌套和遮蔽
只有一个环境没有办法处理嵌套变量声明的情况。例如：
```
var volume = 11;
volume = 0;
{
    var volume = 3;
    print volume;
}
```
括号内重新声明了一个局部变量`volume`，假设只有一个全局的环境，在进入括号时修改了变量的值，退出时无论是删除还是重置都会导致问题。因为外部还有同名的变量。当一个局部变量和外部作用域中的变量同名时，他会 __遮盖（ shadow ）__ 了外部的那个变量。我们需要在进入一个新的块结构的时候，我们需要保留定义在外部作用域的变量，这样在退出这个结构的时候它们依旧存在。因此，对于每个块结构，我们都定义一个全新的环境，这个环境只包含定义在那个作用域的变量。当我们退出这个块，我们就丢掉它的环境并恢复为之前的环境。对于没有被内部变量遮盖的外部变量，解释器在搜索变量的时候，不仅仅要在最里面的环境中搜索，还需要搜索所有外部的环境。因此，我们将环境链接起来，每个环境都有一个引用指向它的直接外部作用域。当查找一个变量的时候，我们从最内部开始，遍历整个链，直到找到这个变量。这样，我们就实现了遮盖的效果。
>整个代码的环境会组成一个树形结构，但是在某一个时刻只存在一个通路（如果语言支持并发，就不是了）。这个结构叫做 __[父指针树（ parent-pointer tree ）](https://en.wikipedia.org/wiki/Parent_pointer_tree)__ 也叫做 __仙人掌栈（ cactus stack ）__

首先修改`Environment`类，添加一个指向外部环境的引用。`define()`方法不需要修改，新变量的声明总是声明在最内部的作用域中，但是涉及到操作已有变量的赋值和查找操作就需要遍历整个链去查找了。
```Java
//Environment.java
class Environment{
    final Environment enclosing;
    Environment(){
        //全局环境没有外部环境
        enclosing = null;
    }
    Environment(Environment enclosing){
        //嵌套的环境初始化需要传入外部环境
        this.enclosing = enclosing;
    }
    public Object get(Token name ){
        if(values.containsKey(name.lexeme)){
            return get(name.lexeme);
        }
        if(enclosing != null){
            return enclosing.get(name);
        }
        throw new RuntimeError(name,"Undefined variable '" + name.lexeme + "'.");
    }
    void assign(TOken name,Object value){
    if(values.containsKey(name.lexeme)){
        values.put(name.lexeme,value);
        return;
    }
    if(enclosing != null){
        enclosing.assign(name,value);
        return;
    }
    throw new RuntimeError(name,"Undefined variable '" + name.lexeme + "'.");
}
```
#### 快语法和语义
```
statement → exprStmt
          | printStmt
          | block;
block     → "{" declaration* "}";
```
一个块就是有花括号括起来的一系列语句或者声明（可能为空）的语句。而这个块可以出现在任何允许语句出现的地方。语法如下：
```Java
//GenerateAst.java
defineAst(outputDir,"Stmt",Arrays.asList(
    "Block   : List<Stmt> statements",
    "Expression: Expr expression",
))
```
解析和解释块语句的代码：
```Java
//Parser.java
if(match(TokenType.LEFT_BRACE)) return new Stmt.Block(block());

private List<Stmt> block(){
    List<Stmt> statements = new ArrayList<>();
    //注意，避免无限循环，isAtEnd的判断是必须的
    while(!check(TokenType.RIGHT_BRACE) && !isAtEnd()){
        statements.add(declaration());
    }
    consume(TokenType.RIGHT_BRACE,"Expect '}' after block.");
    return statements;
}
//Interpreter.java
@Override
public Void visitBlockStmt(Stmt.Block stmt){
    executeBlock(stmt.statements,new Environment(environment));
    return null;
}
void executeBlock(List<Stmt>statements,Environment environment){
    Environment previous = this.environment;
    try{
        this.environment = environment;
        for(Stmt statement : statements){
            execute(statement);
        }
    }finally{
        this.environment = previous;
    }
}
```
之前，`Interpreter`的`environment`字段指向的都是全局环境，现在这个字段代表的是 _当前（ current ）_ 环境。这个环境就是包含被执行代码的最内层作用域。当执行某个给定作用域的代码时，这个方法更新解释器的`environment`字段，访问所有语句，然后在`finally`子句恢复之前的环境，这样即使有异常抛出，也能保证环境恢复。
>让`block()`返回原始的语句列表，让`statement()`将这个列表包装到`Stmt.Block`是为了后续解析函数体能够复用做准备的，在那里不希望将函数体包装在一个`Stmt.Block`中。
>手动设置和恢复环境变量可能不够优雅。另一个方式是显式地给每个访问者方法传入一个环境作为参数。为了“修改”环境，在递归下降处理树的时候，传入不同的环境。这样就不需要恢复环境，因为，新的环境是存在于 Java 的栈中，随着解释器从块访问方法返回时会直接丢弃掉。

>#### 隐式变量声明
>Lox 区分了新变量声明和赋值给一个已经存在的变量。某些语言会将这些折叠为只有赋值语法。这叫做 __隐式变量声明（ implicit variable declaration ）__ ， Python 、 Ruby 和 CoffeeScript 和其他语言都有这个特性。 JavaScript 有显式语法声明变量，但是也可以在赋值的时候创建新的变量。
>如果同样的语法既可以声明变量又可以给变量赋值，那么语言必须要决定当用户意图的行为不够清晰的时候要怎么做。特别的，每个语言必须要选择隐式变量声明和遮盖之间的交互，以及隐式声明的变量的作用域。
>+ 在 Python 中赋值总是在当前函数作用域中创建一个变量，即使在函数外部有声明了同名的变量
>+ Ruby 通过给局部变量和全局变量制定不同的命名规则避免某些歧义。然后 Ruby 在块（ 相比于 C 的块语法这个更像是闭包）中有自己的作用域，因此还是会有这个问题。在 Ruby 中，如果在当前块的外部存在的同名变量，赋值语句会给其赋值，否则会在当前块作用域中创建一个变量。
>+ 在 CoffeeScript 中，在很多方面都效方 Ruby ，这里也类似。它显式的禁止遮盖，规定赋值语句总是赋值给外层作用域中的变量（如果存在这个变量），一直到最外层的作用域。否则，它会在当前作用域中创建一个变量。
>+ 在 JavaScript 中，赋值总是会修改任意外部作用域（指的是沿着环境链一直往上找）中已经存在的变量（如果有的话），否则它会在全局作用域中隐式创建一个变量。
>隐式变量声明的优势是简单，语言的语法更少，用户不需要学习声明这个概念。显式变量声明给用户机会告诉编译器每个变量的类型是什么，以及要给它分配多少存储，而旧的静态类型语言，比如 C ，从中可以获益。这对于动态类型有垃圾回收的的语言来说，就没有什么必要。
>隐式变量声明的缺点有：
>+ 用户的意图是给已存在的变量赋值，但是可能会由于错误的输入导致解释器静默地创建一个新的变量，而用户希望赋值的变量还是旧的。这在 JavaScript 中尤其严重，因为 typo 导致在创建一个全局变量，而这会进一步影响其他代码。
>+ JS ， Ruby 和 CoffeeScript 使用同名已存在的变量，即使这个变量在外部作用域，来判断赋值是否创建一个新变量还是赋值给已经存在的。而这意味着，在外部作用域中增加一个新变量可能会导致老代码的含义变化。曾经的局部变量可能静默地转换为给新的外部变量赋值的赋值语句。
>+ 在 Python 中，你可能想要给当前函数外的某些变量赋值，而不是在带当前函数中创建一个新变量，但是做不到。
>上面这些语言都增加了一些功能和复杂性处理这些问题
>+ 在 JavaScript 中隐式声明全局变量被认为是错误。 JavaScript 的“ Strict mode ”禁止了它并产生编译错误。
>+ Python 增加了一个 `global` 语句，允许在函数中可以显式给全局变量赋值。随着函数式编程和嵌套函数的流行，它又增加了类似的`nolocal`语句，给在外部函数中的变量赋值。
>+ Ruby 扩展了语法，允许声明某些变量在块里面是显式局部的，即使在外部作用域中存在同名的变量。
>作者认为，隐式变量声明在大多数脚本语言还是指令式的，代码很平铺直叙的时候有用。随着程序员对于深度嵌套，函数式编程，闭包非常熟悉的时候，访问外部作用域的变量就变得很常见了。这样，程序员就很容易进入到棘手的情况，用户对于赋值语句的意图到底是创建一个新变量还是重用外部变量就很不清晰了。

## 记
本章处理了语句之后，就在第六章中提到的`parser`处理不了表达式`1a+2`或者`1+2a`的情况。对于第一种情况，`scanner`对于这两个表达式分别会产生对应 token ：`1`，`a`，`+`，`2`。对于第二种情况，`scanner`会产生`1`，`+`，`2`，`a`。对于第一种情况，那第六章实现的`parser`中，对于第一种情况，表达式的解析的 AST 结果只是`1`，对于第二种情况解析出来的AST是`1+2`。当引入语句之后，在解析到`a`时，会认为是不合法的语句格式，因此报错了。