package org.keycloak.test.page;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class ConsentPage {

    @FindBy(id = "kc-login")
    private WebElement submitButton;

    @FindBy(id = "kc-cancel")
    private WebElement cancelButton;

    public void confirm() {
        submitButton.click();
    }

    public void cancel() {
        cancelButton.click();
    }

    public boolean isCurrent(WebDriver driver) {
        return driver.findElement(By.id("kc-page-title")).getText().startsWith("Grant Access to ");
    }

}