package com.craftinginterpreters.lox;

import java.util.List;

import com.craftinginterpreters.lox.Expr.Assign;
import com.craftinginterpreters.lox.Expr.Binary;
import com.craftinginterpreters.lox.Expr.Grouping;
import com.craftinginterpreters.lox.Expr.Literal;
import com.craftinginterpreters.lox.Expr.Unary;
import com.craftinginterpreters.lox.Expr.Variable;
import com.craftinginterpreters.lox.Stmt.*;


/*
 * 解释器类，主要的工作，使用visitor模式，后序遍历表达式树，计算表达式的值
 */
public class Interpreter implements Expr.Visitor<Object>,Stmt.Visitor<Void> {

    private Environment environment = new Environment();

    public void interpret(List<Stmt> statements ){
        try {
            for(Stmt statement:statements){
                execute(statement);
            }
        }catch (RuntimeError error){
            Lox.runtiemError(error);
        }
    }
    private String stringify(Object value) {
        if(value == null) return "nil";
        if(value instanceof Double) {
            String text = Double.toString((Double)value);
            if(text.endsWith(".0")) {
                //Lox 不区分整型和浮点型，所以需要去掉小数点
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }
        return value.toString();
    }
    @Override
    public Object visitBinaryExpr(Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);
        var value = switch(expr.operator.getType()){
            case MINUS -> {
                checkNumberOperands(expr.operator, left, right);
                yield ((double) left - (double) right);
            }
            case SLASH -> {
                checkNumberOperands(expr.operator, left, right);
                yield ((double) left / (double) right);
            }
            case STAR -> {
                checkNumberOperands(expr.operator, left, right);
                yield ((double) left / (double) right);
            }
            case PLUS -> {
                if (left instanceof Double lDouble && right instanceof Double rDouble) {
                    yield lDouble + rDouble;
                }
                if (left instanceof String lString && right instanceof String rString) {
                    yield lString + rString;
                }
                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings.");
            }
            case GREATER ->{ 
                checkNumberOperands(expr.operator, left, right);
                yield ((double) left > (double) right);
            }
            case GREATER_EQUAL -> {
                checkNumberOperands(expr.operator, left, right);
                yield ((double) left >= (double) right);
            }
            case LESS -> {
                checkNumberOperands(expr.operator, left, right);
                yield ((double) left < (double) right);
            }
            case LESS_EQUAL -> {
                checkNumberOperands(expr.operator, left, right);
                yield ((double) left <= (double) right);
            }
            case BANG_EQUAL -> {
                yield !isEqual(left, right);
            }
            case EQUAL_EQUAL -> {
                yield isEqual(left, right);
            }
            default -> null;
        };
        return value;
    }

    @Override
    public Object visitGroupingExpr(Grouping expr) {
        return evaluate(expr);
    }

    @Override
    public Object visitLiteralExpr(Literal expr) {
        return expr.value;
    }

    /* 
     * 
    */
    @Override
    public Object visitUnaryExpr(Unary expr) {
        Object right = evaluate(expr);
        return switch (expr.operator.getType()) {
            case MINUS ->  {
                checkNumberOperand(expr.operator, right);
                yield -(double)right;
            }
            case BANG ->  !isTruthy(right);
            case null,default->  null;
        };
    }

    private Object evaluate(Expr expr){
        return expr.accept(this);
    }

    private boolean isTruthy(Object object){
        if(object == null) return false;
        if(object instanceof Boolean bool) return bool;
        return true;
    }

    private boolean isEqual(Object a, Object b){
        if(a == null && b == null) return true;
        if(a == null) return false;
        return a.equals(b);
    }

    private void checkNumberOperand(Token operator, Object operand){
        if(operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right){
        if(left instanceof Double && right instanceof Double) return;
        
    }
    @Override
    public Void visitExpressionStmt(Expression stmt) {
      evaluate(stmt.expression);
      return null;
    }
    @Override
    public Void visitPrintStmt(Print stmt) {
      Object value = evaluate(stmt.expression);
      System.out.println(stringify(value));
      return null;
    }

    @Override
    public Object visitVariableExpr(Variable expr) {
        return environment.get(expr.name);
    }
    @Override
    public Void visitVarStmt(Var stmt) {
        Object value = null;
        if(stmt.initializer != null){
            value = evaluate(stmt.initializer);
        }
        environment.define(stmt.name, value);
        return null;
    }

    

    @Override
    public Object visitAssignExpr(Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }

    
    @Override
    public Void visitBlockStmt(Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }


    private void executeBlock(List<Stmt> stmts,Environment environment){
        Environment previous = this.environment;
        try{
            this.environment = environment;
            for(Stmt stmt:stmts){
                execute(stmt);
            }
        }finally{
            this.environment = previous;
        }
    }

    private void execute(Stmt stmt){
        stmt.accept(this);
    }
}
