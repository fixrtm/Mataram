# Mataram: the java decompiler

a decompiler which decompiles class file to a compilable java file.

## Why not FernFlower or JD-core?

FernFlower sometimes is not able to infer the type of constant values.
e.g. the value `0` will decompiled into `false`

JD-Core cannot decompile calling static method with instance.

`this.staticMethod()` will be compiled to `this; staticMethod()`.

## Special Thanks

@nagise : The person who named this project.
