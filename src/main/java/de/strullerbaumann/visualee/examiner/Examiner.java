/*
 Copyright 2013 Thomas Struller-Baumann, struller-baumann.de

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package de.strullerbaumann.visualee.examiner;

import de.strullerbaumann.visualee.dependency.entity.Dependency;
import de.strullerbaumann.visualee.dependency.entity.DependencyType;
import de.strullerbaumann.visualee.javasource.boundary.JavaSourceContainer;
import de.strullerbaumann.visualee.javasource.entity.JavaSource;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Scanner;

/**
 *
 * @author Thomas Struller-Baumann <thomas at struller-baumann.de>
 */
public abstract class Examiner {

   private static final String[] JAVA_TOKENS = {"void", "private", "protected", "transient", "public", "static", "@"};

   protected abstract boolean isRelevantType(DependencyType type);

   public abstract void examine(JavaSource javaSource);

   protected static Scanner getSourceCodeScanner(String sourceCode) {
      Scanner scanner = new Scanner(sourceCode);
      scanner.useDelimiter("[ \t\r\n]+");
      return scanner;
   }

   protected static String getClassBody(String sourceCode) {
      // todo evtl doch interface class abstract abfragen kann ja ein produces mit { sein
      StringBuilder classBody = new StringBuilder();
      boolean isInBodyNow = false;
      try (Scanner scanner = new Scanner(sourceCode)) {
         scanner.useDelimiter("[\n]+");
         while (scanner.hasNext()) {
            String token = scanner.next();
            if (!isInBodyNow) {
               if (token.indexOf('{') > -1) {
                  isInBodyNow = true;
               }
            } else {
               classBody.append(token).append("\n");
            }
         }
      }

      return classBody.toString();
   }

   protected static DependencyType getTypeFromToken(String token) {
      DependencyType type = null;
      if (token.indexOf("@EJB") > -1) {
         type = DependencyType.EJB;
      }
      if (token.indexOf("@Inject") > -1) {
         type = DependencyType.INJECT;
      }
      if (token.indexOf("@Observes") > -1) {
         type = DependencyType.OBSERVES;
      }
      // Identify @Produces form Inject (@Produces form WS is @Produces(...)
      // Inject: http://docs.oracle.com/javaee/6/api/javax/enterprise/inject/Produces.html
      // WS: http://docs.oracle.com/javaee/6/api/javax/ws/rs/Produces.html
      if (token.indexOf("@Produces") > -1 && token.indexOf("@Produces(") < 0) {
         type = DependencyType.PRODUCES;
      }
      if (token.indexOf("@OneToOne") > -1) {
         type = DependencyType.ONE_TO_ONE;
      }
      if (token.indexOf("@OneToMany") > -1) {
         type = DependencyType.ONE_TO_MANY;
      }
      if (token.indexOf("@ManyToOne") > -1) {
         type = DependencyType.MANY_TO_ONE;
      }
      if (token.indexOf("@ManyToMany") > -1) {
         type = DependencyType.MANY_TO_MANY;
      }
      return type;
   }

   protected static int countChar(String string, char char2Find) {
      int count = 0;
      for (int i = 0; i < string.length(); i++) {
         if (string.charAt(i) == char2Find) {
            count++;
         }
      }
      return count;
   }

   protected static String scanAfterClosedParenthesis(String currentToken, Scanner scanner) {
      Deque<Integer> stack = new ArrayDeque<>();
      int iStack = 1;

      int countParenthesis = countChar(currentToken, '(');
      for (int iCount = 0; iCount < countParenthesis; iCount++) {
         stack.push(iStack);
         iStack++;
      }
      String token = scanner.next();
      boolean bEnd = false;
      while (stack.size() > 0 && !bEnd) {
         if (getTypeFromToken(token) != null) {
            break;
         }
         if (token.indexOf('(') > -1) {
            int countOpenParenthesis = countChar(token, '(');
            for (int iCount = 0; iCount < countOpenParenthesis; iCount++) {
               stack.push(iStack);
               iStack++;
            }
         }
         if (token.indexOf(')') > -1) {
            int countClosedParenthesis = countChar(token, ')');
            for (int iCount = 0; iCount < countClosedParenthesis; iCount++) {
               stack.pop();
               iStack++;
            }
         }
         if (scanner.hasNext()) {
            token = scanner.next();
         } else {
            bEnd = true;
         }
         iStack++;
      }

      return token;
   }

   protected void createDependency(String className, DependencyType type, JavaSource javaSource) {
      JavaSource injectedJavaSource = JavaSourceContainer.getInstance().getJavaSourceByName(className);
      if (injectedJavaSource == null) {
         // Generate a new JavaSource, which is not explicit in the sources (e.g. Integer, String etc.)
         injectedJavaSource = new JavaSource(className);
         JavaSourceContainer.getInstance().add(injectedJavaSource);
      }
      Dependency dependency = new Dependency(type, javaSource, injectedJavaSource);
      javaSource.getInjected().add(dependency);
   }

   protected static boolean isAJavaToken(String token) {
      for (String javaToken : JAVA_TOKENS) {
         if (token.indexOf(javaToken) > - 1) {
            return true;
         }
      }

      return false;
   }

   // TODO Unittest
   protected String jumpOverJavaToken(String token, Scanner scanner) {
      String nextToken = token;
      while (isAJavaToken(nextToken)) {
         if (nextToken.startsWith("@") && nextToken.indexOf('(') > -1 && !nextToken.endsWith(")")) {
            nextToken = scanAfterClosedParenthesis(nextToken, scanner);
         } else {
            nextToken = scanner.next();
         }
      }
      return nextToken;
   }

   protected static void findAndSetPackage(JavaSource javaSource) {
      Scanner scanner = Examiner.getSourceCodeScanner(javaSource.getSourceCode());
      while (scanner.hasNext()) {
         String token = scanner.next();
         if (javaSource.getPackagePath() == null && token.equals("package")) {
            token = scanner.next();
            String packagePath = token.substring(0, token.indexOf(';'));
            javaSource.setPackagePath(packagePath);
         }
      }
   }
}