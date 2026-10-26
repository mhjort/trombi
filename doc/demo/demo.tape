# Renders doc/demo/demo.gif used in README.md.
# Install VHS (https://github.com/charmbracelet/vhs) and run from the repository root:
#   vhs doc/demo/demo.tape

Output doc/demo/demo.gif

Require clojure

Set Shell "bash"
Set FontSize 15
Set Width 1200
Set Height 500
Set Padding 20
Set Theme "Dracula"
Set TypingSpeed 35ms

# Short prompt and downloaded dependencies before recording starts
Hide
Type "export PS1='$ ' && clojure -M:dev -e nil && clear"
Enter
Sleep 10s
Show

Type "cat doc/demo/demo.clj"
Sleep 300ms
Enter
Sleep 6s

Type "clear && clojure -M:dev doc/demo/demo.clj"
Sleep 300ms
Enter

# Skip JVM startup
Hide
Sleep 2500ms
Show

Sleep 13s
