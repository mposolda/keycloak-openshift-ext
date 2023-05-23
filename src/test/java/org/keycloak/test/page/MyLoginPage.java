package org.keycloak.test.page;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class MyLoginPage extends LoginPage {

    public boolean isCurrent(WebDriver driver) {
        WebElement webElement = driver.findElement(By.id("username"));
        return webElement.isDisplayed();
    }
}
