#include <iostream>

using namespace std;

int square(int n);

int main()
{
   cout << square(3) << endl;
   cout << "Expected: 9" << endl;

   cout << square(-3) << endl;
   cout << "Expected: 9" << endl;
}
