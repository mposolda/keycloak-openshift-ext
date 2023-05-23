package org.keycloak.test.page;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class ErrorPage {

//    @ArquillianResource
//    protected OAuthClient oauth;

    @FindBy(className = "instruction")
    private WebElement errorMessage;

    @FindBy(id = "backToApplication")
    private WebElement backToApplicationLink;

    public String getError() {
        return errorMessage.getText();
    }

    public void clickBackToApplication() {
        backToApplicationLink.click();
    }

    public String getBackToApplicationLink() {
        if (backToApplicationLink == null) {
            return null;
        } else {
            return backToApplicationLink.getAttribute("href");
        }
    }

    public boolean isCurrent(WebDriver driver) {
        return driver.findElement(By.id("kc-page-title")).getText().equals("We are sorry...");
    }

//    @Override
//    public void open() {
//        throw new UnsupportedOperationException();
//    }

}
