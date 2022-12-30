package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

//TODO:shell exit code 查询
public class Lox {
    /*
    这个字段的作用是为了确保不会执行已经有已知错误的代码，并返回非0退出码(读取文件时)。
    而com.craftinginterpreters.lox.Lox.report和com.craftinginterpreters.lox.Lox.error方法放在Lox类的
    主要原因也是要设置这个值
     */
    private static boolean hadError = false;
    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage:jlox [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));
        if(hadError) System.exit(65);
    }

    private static void runPrompt() throws IOException {
        InputStreamReader inputStreamReader = new InputStreamReader(System.in);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        while (true) {
            System.out.println("> ");
            String line = bufferedReader.readLine();
            if (line == null) break;
            run(line);
            //reset标志，这样就不会退出交互式命令行
            hadError = false;
        }
    }

    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        Expr expr = parser.parse();
        if(hadError) return;
        System.out.println(new AstPrinter().print(expr));
        
    }

    public static void error(int line, String message) {
        report(line, "", message);
    }

    public static void error(Token token,String message){
        if(token.getType() == TokenType.EOF){
            report(token.getLine(), "at end", message);
        }else{
            report(token.getLine(), "at '" + token.getLexeme() + "'", message);
        }
    }
    /*
    好的工程实践是将产生错误的代码和报告错误的代码分离开。
    在解释器前端的各个阶段中，都会检测到错误，但是知道应该
    如何呈现给用户并不是它们的工作。
    在一个完整的语言实现中，可能会有多个显示错误的方式：stderr，IDE的错误窗口，日志文件等等。
    这些代码不应该散布于扫描器和解析器之间。
    理想状态，我们应该有一个实际的抽象表示，例如ErrorReporter接口，传递给scanner和parser，
    这样我们可以替换成各种报告策略。
     */
    private static void report(int line, String where, String message) {
        System.out.println("[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }
}
