package com.AddressBook;

import java.io.Console;

public class UserInput {

    private static UserInput instance;

    private Console cnsl;

    private UserInput() {
        cnsl = System.console();
    }


    public static UserInput getInstance() {
        return instance;
    }


    public String getNextInput() {
        return cnsl.readLine();
    }

    public void sendOutput(String output) {
        cnsl.writer().print(output);
    }

}