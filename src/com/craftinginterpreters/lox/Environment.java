package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    private final Environment enclosing;
    private final Map<String,Object> values = new HashMap<>();

    

    Environment() {
        enclosing = null;
    }

    Environment(Environment enclosing){
        this.enclosing = enclosing;
    }

    void define(Token name,Object value){
        values.put(name.getLexeme(), value);
    }

    Object get(Token name){
        if(values.containsKey(name.getLexeme())){
            return values.get(name.getLexeme());
        }
        if(enclosing != null) return enclosing.get(name);
        throw new RuntimeError(name,"Undfined variable '"+name.getLexeme()+"'.");
    }

    void assign(Token name, Object value){
        if(values.containsKey(name.getLexeme())){
            values.put(name.getLexeme(), value);
            return;
        }
        if(enclosing != null){
            enclosing.assign(name,value);
            return;
        }
        throw new RuntimeError(name,"Undefined variable '"+name.getLexeme()+"'.");
    }
}
