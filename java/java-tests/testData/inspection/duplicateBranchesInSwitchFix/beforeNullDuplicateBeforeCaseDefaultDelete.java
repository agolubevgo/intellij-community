// "Delete redundant 'switch' branch" "false"
class Test {
  void foo(Object o) {
    switch (o) {
      case null:
        System.out.println(42)<caret>;
        break;
      case default:
        System.out.println(42);
    }
  }
}
