package com.lezai.samples.task;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Dog {
    private String name;

    private String color;

    public void run() {
        System.out.println("Dog " + name + " is running.");
    }

    public void eat() {
        System.out.println("Dog " + name + " is eating.");
    }

    public void sleep() {
        System.out.println("Dog " + name + " is sleeping.");
    }

    public void wagTail() {
        System.out.println("Dog " + name + " is wagging its tail.");
    }

    public void shake() {
        System.out.println("Dog " + name + " is shaking.");
    }

    public void rollOver() {
        System.out.println("Dog " + name + " is rolling over.");
    }

    public void play() {
        System.out.println("Dog " + name + " is playing.");
    }

    public void fetch() {
        System.out.println("Dog " + name + " is fetching.");
    }

    public void bark() {
        System.out.println("Dog " + name + " is barking.");
    }
}
