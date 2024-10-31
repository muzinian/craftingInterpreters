## 估值表达式(evaluating expressions)
语言的实现可以有多种方式让计算机执行用户源代码的命令。他们可以编译成机器码，翻译成另一个高级语言，或者简化为一个虚拟机的字节码格式运行。这里，我们选择最简单最短的路径并执行语法树自身。

现在我们的解析器只支持表达式。所以，为了“执行”代码，我们将会估值一个表达式并产生一个值。对于我们可以解析的表达式语法我们需要对应的代码，这些代码知道如何估值树并产生一个结果。这就产生了两个问题：
1. 我们可以产生什么类型的值？
2. 我们要如何组织这些代码块？
### 表示值
在 Lox 中，值可以通过字面量创建，通过表达式计算，并存储在变量中。用户会把他们看作 Lox 对象，但是他们是由我们编写解释器的底层语言所实现的。这意味着要在 Java 的静态类型和 Lox 的动态类型搭建桥梁。 Lox 的变量可以存储任意 Lox 类型的值，是真在不同的时间点存储不同类型的值。什么 Java 类型可以用来表示它们？
>这里，”值“和”对象“是可以互换的

>后面，我们会在 C 的解释器中对它们稍作区分。但主要是为了在实现的两个不同的地方有唯一的术语--就地数据(in-place) vs 堆分配数据。从用户的角度来说，这两个词是同义词


由于 Java 变量有静态类型，我们需要要在运行时能够确认他持有的值是什么类型的。当解释器执行一个`+`操作，他需要区分是将两个数字相加还是连接两个字符串。所以我们需要能够持有数字，字符串，Boolean以及其他的类型，这就是 Java 的`java.lang.Object`。在解释器中如果我们需要存储一个 Lox 值，就使用`Object`作为类型， Java 对基本类型有对应的包装类型，而它们的父类都是`Object`，因此我们可以使用它们表示 Lox 的内建类型：
|Lox type|Java 表示|
|-|-|
|任意 Lox 值|`Object`|
|`nil`|`null`|
|Boolean|`Boolean`|
|number|`Double`|
|string|`String`|

根据静态类型 Object 的值，我们可以使用 Java 内建的`instanceof`操作判断运行时值是数字，还是字符串或者是别的什么值。换句话说， JVM 拥有的对象表示方便的给了我们实现 Lox 内建类型需要的所有事情。后面添加 Lox 的函数，类和实例时还需要做一些其他事情。但是现在来看，是足够的了。
>对于值，我们还需要管理它们的内存，而 Java 的 GC 已经帮我们做好了

### 估值表达式
我们可以将估值表达式的代码逻辑放到语法树类里，像是一个`interpret()`方法。事实上，我们可以让每个语法树节点“解释自己”，这就是 GoF 的[解释器模式](https://en.wikipedia.org/wiki/Interpreter_pattern)。但是如果我们把所有这些逻辑揉到树的类里，会让代码变的很脏。所以我们会重用[访问者模式](https://craftinginterpreters.com/representing-code.html#the-visitor-pattern)。我们现实现一个新的类：
```java
class Interpreter implements Expr.Visitor<Object>{
}
```
这个类是一个 visitor 。`visit`方法返回的值是`Object`。我们需要为解析器产生的四个表达式树类定义访问方法。
#### 估值字面量
表达式树的叶子，也是组成其他所有表达式的语法原子单元，就是字面量。字面量几乎就是已经是值了，但是区别还是很重要的。字面量是可以产生值的 _一个语法 (a bit of syntax)_。 很多值是由计算产生的，它自身不会出现在代码的任意地方。它们不属于字面量。字面量来自于 parser 领域。值（ Value ）是一个解释器概念，属于运行时世界的一部分。
>下一张实现变量的时候，我们会添加一个标识符表达式，也是叶子结点
类似于将字面量 token 转换为字面量语法树节点，我们将字面量语法树节点转换为运行时值。
```java
@Override
public Object visitLiteralExpr(Expr.Literal expr){
    return expr.value;
}
```
在扫描的时候，我们已经提早产生了运行时值并将它塞入到了 token 中。 parser 取到这个值并塞入到字面量树节点，所以估值一个字面量对我们来说很简单。

#### 估值括号
```Java
@Override
public Object visitGroupingExpr(Expr.Grouping expr){
    return evaluate(expr.expression);
}
```
分组节点有一个引用，它指向括号内表达式的内部节点，为了估值 grouping ，我们需要递归的估值子表达式并返回
```Java
private Object evaluate(Expr expr){
    return expr.accept(this);
}
```
>某些语言不会为括号定义树节点，当解析括起来的表达式时，他们简单的返回内部表达式的节点。在 Lox 中，我们会为括号创建一个节点，因为我们稍后需要它，为了能正确地处理赋值表达式的左边。

#### 估值一元表达式
一元表达式也有单独的一个子表达式需要我们先估值：
```Java
public Object visitUnaryExpr(Expr.Unary expr){
    Object right = evaluate(epxr.right);
    switch(expr.operator.type){
        case MINUS:
            return -(double)right;
        case BANG:
            return !isTruthy(right);
    }
    //Unreachable
    return null;
}
```
我们先估值操作数表达式，然后我们对结果应用一元操作符。现在有两个不同的一元表达式，由操作符token的类型标识。`-`会对子表达式的结果求负。子表达式必须是一个数字。由于在Java中我们不能静态的知道它是不是数字，我们在执行操作前转换（cast）它。当在运行时对`-`进行估值时会执行类型转换。这是让一个语言动态类型的核心。只有在对一元表达式的操作数子表达式估值完成后才能估值一元操作符。因此，我们对树的遍历是后序遍历(__post-order traversal__)，对于节点的估值要在子节点之后。

#### 真性和假性
当估值逻辑非的时候，涉及到了在 Lox 中，如何对待除了 `true`和`false`之外的值的真假性问题。有两种做法：一，报错，不对值做任何隐式转换，但是大部分动态语言不会这样；二，将所有类型的全部可能值分成两类，一类定义为真，一类为假。这个分割是很随意，在某些语言中甚至奇怪。
>在 JavaScript 中，字符串是为真，但是空字符串不是。数组是真的，空数组也是。`0`为假，但是字符串 `"0"`，Python 的空字符串是假，其他空序列也是假。 PHP 中，`0`和`"0"`都是假。大多数其他非空字符串是真。

Lox 中，只有`false`和`nil`是假，其他都是真。因此，实现如下：
```Java
private boolean isTruthy(object object){
    if(object == null) return false;
    if(object instanceof Boolean) return (boolean)object;
    return true;
}
```

#### 估值二元操作符
```Java
public Object visitBinaryExpr(Expr.Binary expr){
    Object left = evaluate(expr.left);
    Object right = evaluate(expr.right);
    switch(expr.operator.type){
        case MINUS:
            return (double)left - (double)right;
        case SLASH:
            return (double)left / (double)right;
        case STAR:
            return (double)left * (double) right;
        case PLUS:
            if(left instanceof Double && right instanceof Double){
                return (double) left + (double) right;
            }
            if(left instanceof String && right instanceof String){
                return (String) left + (String) right;
            }
            break;
        case GREATER:
            return (double) left > (double) right;
        case GERATER_EQUAL:
            return (double) left > (double) right;
        case LESS:
            return (double) left < (double) right;
        case LESS_EQUAL:
            return (double) left <= (double) right;
        case BANG_EQUAL:
            return !isEqual(left,right);
        case EQUAL_EQUAL:
            return isEqual(left,right);
    }
    return null;
}
```
除了`+`特殊些，别的算数操作符跟一元取负操作符的区别是我们需要估值两个操作数。当估值二元表达式的时候，我们的估值方式是从左到右，这涉及到了语言的语义细节。当操作数有副作用的时候，这对语言的用户来说就会有感知。由于`+`还需要处理字符串拼接，我们需要动态检查值的类型然后根据类型选择合适的处理逻辑。所以我们需要对象表示支持`instanceof`。对于比较操作符，跟算数操作符相比，前者只会返回`Boolean`，后者返回的跟操作数类型一致。而对于等值操作符，它支持任意类型的比较，甚至混合的都可以。
```Java
private boolean isEqual(Object a,Object b){
    if(a == null && b == null) return true;
    if(a == null) return false;
    return a.equals(b);
}
```
这里，我们用 Java 表示 Lox 对象的方式细节就重要了。我们需要正确的实现 Lox 的相等，而这可能与 Java 的相等性不同。这里，我们已经可以正确解析有效的 Lox 表达式。
>`(0 / 0) == (0 / 0)` 根据 [IEEE754](https://en.wikipedia.org/wiki/IEEE_754)，除`0`得到的是 __NaN__("not a number")值，而 NaN 不等于自身。对于 Java ，基本类型`double`的`==`操作满足了这个要求，但是对于包装类型`Double`类是没有的。由于 Lox 使用了后者，所以没有遵循 IEEE。

### 运行时错误
上面的代码会对类型做强制转换，而这可能失败。一个可用的语言，应当优雅的处理用户代码的错误。程序设计当然可以按照 C 语言的做法不检测/报告任何类型错误。这是 C 语言灵活快速的源泉，也是危险的源头。少数现代语言允许这样的不安全操作。但是大多数 __内存安全(memory safe)__ 的语言会通过静态的和运行时检查确保程序永远不会错误的解释内存中存的值。

之前的内容都是 _语法_ 或者 _静态_ 错误。这是在代码执行前检查的。运行时错误是程序语义要求我们在程序运行期间检测和报告的失败。现在的代码在出现转换失败时， JVM 会抛出`ClassCastException`，对整个栈展开并退出应用输出 Java 调用栈。 一个好的语言应该对用户隐藏自己的实现细节，因此应该反馈的是 Lox 发生了运行时异常并给出和语言以及程序相关的错误信息。当发生运行时错误的时候我们需要停止估值表达式，但不应该结束解释器。

#### 检测运行时错误
目前的解释器使用递归方法调用估值嵌套表达式，因此再出错时需要对这些都执行栈展开。 Java 的异常本身可以执行这个操作，但是 Lox 需要包装下 Java 的转换异常，这样我们可以按照自己的想法处理这个异常。首先，在对操作数执行转换前，需要闲检测对象自身的类型。检测方法：
```Java
private void checkNumberOperand(Token operator,Object operand){
    if(operand instanceof Double) return;
    throw new RuntimeError(operator,"Operand must be a number.");
}

private void checkNumberOperands(Token operator,Object left,Object right){
    if(left instanceof Double && right instanceof Double) return;
    throw new RuntimeError(operator,"Operand must be a number.");
}
```
一元操作符只需要对`-`的操作数做检测，而对于二元操作符，由于`+`已经判断了对象类型，所以如果没有进入任何`if`，就抛异常。
```Java
class RuntimeError extends RuntimeException{
    final Token token;
    RuntimeError(Token token,String message){
        super(message);
        this.token = token;
    }
}
```
```Java
public Object visitUnaryExpr(Expr.Unary expr){
    Object right = evaluate(epxr.right);
    switch(expr.operator.type){
        case MINUS:
            checkNumberOperand(expr.operator,right);
            return -(double)right;
        case BANG:
            return !isTruthy(right);
    }
    //Unreachable
    return null;
}

public Object visitBinaryExpr(Expr.Binary expr){
    Object left = evaluate(expr.left);
    Object right = evaluate(expr.right);
    switch(expr.operator.type){
        case MINUS:
            checkNumberOperands(expr.operator,left,right);
            return (double)left - (double)right;
        case SLASH:
            checkNumberOperands(expr.operator,left,right);
            return (double)left / (double)right;
        case STAR:
            checkNumberOperands(expr.operator,left,right);
            return (double)left * (double) right;
        case PLUS:
            if(left instanceof Double && right instanceof Double){
                return (double) left + (double) right;
            }
            if(left instanceof String && right instanceof String){
                return (String) left + (String) right;
            }
            throw new RuntimeError(expr.operator,"Operands must be all numbers or strings");
        case GREATER:
            checkNumberOperands(expr.operator,left,right);
            return (double) left > (double) right;
        case GERATER_EQUAL:
            checkNumberOperands(expr.operator,left,right);
            return (double) left > (double) right;
        case LESS:
            checkNumberOperands(expr.operator,left,right);
            return (double) left < (double) right;
        case LESS_EQUAL:
            checkNumberOperands(expr.operator,left,right);
            return (double) left <= (double) right;
        case BANG_EQUAL:
            return !isEqual(left,right);
        case EQUAL_EQUAL:
            return !isEqual(left,right);
    }
    return null;
```
注意这里还有一个语义选择，我们是在估值完所有操作数之后才进行类型转换的。如果操作数有副作用，会执行完这些有副作用的表达式之后才会报错，比如有一个`say()`，它会打印所有输入的参数，那么在估值表达式`say("left") - say("right")`时，会打印出`left`和`right`后再报运行时错误。我们也可以规定先检测完左操作数之后，在估值有操作数。

### 将解释器串起来
`Interpreter`类的核心是`visit`方法，所有的工作都发生在这里。它的公开 API 只有一个方法：
```Java
void interpret(Expr expression){
    try{
        Object value = evaluate(expression);
        System.out.println(stringify(value));
    }catch(RuntimeError error){
        Lox.runtimeError(error);
    }
}
private String stringify(Object object){
    if(object == null) return "nil";
    if(Object instanceof Double){
        String text = object.toString();
        if(text.endWith(".0")){
            text = text.substring(0,text.length() - 2);//java区分了浮点型和整数类型，Lox 不在乎这些，专门hack
        }
        return text;
    }
    return object.toString();
}
``` 
它的输入是语法树然后对它进行估值，成功`evaluate()`就返回一个`Object`作为结果值，而最终会通过`stringify`转为string，失败就抛出异常。

#### 报告运行时错误
Lox 是在主类中处理错误，我们在 Lox 中处理这个问题，这允许我们优雅的继续。
```Java
static void runtimeError(RuntimeError error){
    System.err.println(error.getMessage() + "\n[line" + error.token.line + "]");
    hadRuntimeError = true;
}
```
```Java
static boolean hadRuntimeError = false;
```
我们还在代码中设置了标志`hadRuntimeError`，这个是在`Lox`类中。当是从一个文件中运行的 Lox 脚本，如果判断它的值为`false`就会异常退出。但如果是 REPL ，我们就不关心运行时错误了，而是在报告错误后让用户重新输入并继续
#### 运行解释器
我们在代码中初始化了一个`Interpreter`，设置变量为`static`。这样做的原因是因为后面`interpreter`会存储全局变量，这些变量应该持续到整个 REPL 会话。目前的`interpreter`还很原始，但是我们使用的`Interpreter`类和`Visitor`模式为后续的章节--变量，函数，等等--建立的框架。
> ##### 设计笔记：静态和动态类型
> 静态语言在编译时期检查和报告类型错误，动态语言将检测延迟到运行时尝试某个操作前，这两个不是非黑即白。即使是最静态类型的语言也会在运行时做某些类型检测。他们的类型系统静态的检查大部分类型规则，但是会为因为其他操作生成的代码中插入运行时检查。例如， Java 的静态类型系统假设类型转换表达式总是成功，转换后可以静态的把这个类型当作目标类型而且不会产生编译错误，但是向下转换可能会失败。静态检查器能推定转换总是成功而不违反语言的健全性保证的唯一原因是因为这个转换在 _运行时(runtime)_ 会被检查并在失败时抛出异常。在 Java 和 C# 中的更精细的例子是 [协变数组(covariant arrays)](https://en.wikipedia.org/wiki/Covariance_and_contravariance_(computer_science)#Covariant_arrays_in_Java_and_C.23)。它们针对数组的静态子类型规则允许并不是健全的操作。例如：
> ```Java
> Object[] stuff = new Integer[1];
> stuff[0] = "not an int!";
> ```
>这个代码不会产生任何编译错误。第一行将整型数组向上转换并存储到类型是`Object`数组的变量。第二行将一个字符串存到第一个单元格。对象数组静态上允许这个操作，毕竟string是Object，但是，实际上`stuff`指向的实际整数数组在运行时是不允许这样的，因此 JVM 会执行运行时检查确保是合适的类型，否则会抛出对应的异常( ArrayStoreException )。 Java 本可以通过禁止第一行的转换来避免运行时做这种检查，这样数组就是 _不变的(invariant)_，这在静态上是健壮的，但是禁止了常见但安全的操作：只从数组中读取数据。如果从不写入数组协变就是安全的。而这个特性对于不支持范型的 Java1.0 的可用性来说非常重要， Java 的设计者对此在静态安全和性能与灵活性之间做了权衡。很少有现代静态类型语言没有在某些地方做出类似的权衡。即是 Haskell 也允许用户运行没有耗尽匹配（ no-exhaustive matches ）的代码，在设计静态类型语言的时候，请记住，有时候可以在不会牺牲太多静态类型安全性的情况下通过延迟某些检查到运行时执行给用户更多的灵活性。

