# BlueJ-Linting-Core

A core library designed to be used for writing linting extensions for the BlueJ IDE.


This library provides core functionality used by the [Checkstyle][1] and [SonarLint][2] extensions for BlueJ.
It covers datatypes for violations and rule definitions, a ViolationManager that can be used to track these violations. 


A default PackageEventHandler and FilesChangeHandler implementation is provided, for handling when projects open/close and when files are changed/removed. These implementations are optional.

[1]: https://github.com/NTNU-IE-IIR/BlueJ-Checkstyle-Plugin
[2]: https://github.com/NTNU-IE-IIR/BlueJ-SonarLint-Plugin