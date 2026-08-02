package com.loja.useraccount.adapter.in.web;

import com.loja.useraccount.adapter.session.CurrentUser;
import com.loja.useraccount.domain.model.Address;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.in.AddAddressUseCase;
import com.loja.useraccount.domain.port.in.DeleteAddressUseCase;
import com.loja.useraccount.domain.port.in.ListAddressesUseCase;
import com.loja.useraccount.domain.port.in.SetDefaultAddressUseCase;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * JSF managed bean for address book management.
 * Thin adapter: delegates to address use cases, no business logic here (SRP).
 */
@Named
@RequestScoped
public class AddressBookBean {

    @Inject
    @CurrentUser
    private User currentUser;

    @Inject
    private AddAddressUseCase addAddressUseCase;

    @Inject
    private DeleteAddressUseCase deleteAddressUseCase;

    @Inject
    private ListAddressesUseCase listAddressesUseCase;

    @Inject
    private SetDefaultAddressUseCase setDefaultAddressUseCase;

    private List<Address> addresses;

    // Form fields for adding a new address
    @NotBlank(message = "Street is required")
    @Size(max = 255, message = "Street must be at most 255 characters")
    private String street;

    @Size(max = 20, message = "Number must be at most 20 characters")
    private String number;

    @Size(max = 100, message = "Complement must be at most 100 characters")
    private String complement;

    @Size(max = 100, message = "Neighborhood must be at most 100 characters")
    private String neighborhood;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must be at most 100 characters")
    private String city;

    @NotBlank(message = "State is required")
    @Size(min = 2, max = 2, message = "State must have exactly 2 characters")
    private String state;

    @NotBlank(message = "Postal code is required")
    @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "Invalid postal code format")
    private String postalCode;

    @Size(max = 50, message = "Label must be at most 50 characters")
    private String label;

    private boolean setAsDefault;

    @PostConstruct
    public void loadAddresses() {
        addresses = new ArrayList<>(listAddressesUseCase.listAddresses(currentUser.getId()));
    }

    public void addAddress() {
        try {
            addAddressUseCase.addAddress(currentUser.getId(), street, number, complement,
                    neighborhood, city, state, postalCode, label, setAsDefault);
            loadAddresses();
            clearForm();
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Success", "Address added"));
        } catch (IllegalArgumentException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage()));
        }
    }

    public void deleteAddress(Long addressId) {
        try {
            deleteAddressUseCase.deleteAddress(currentUser.getId(), addressId);
            loadAddresses();
        } catch (IllegalStateException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage()));
        }
    }

    public void setDefault(Long addressId) {
        try {
            setDefaultAddressUseCase.setDefaultAddress(currentUser.getId(), addressId);
            loadAddresses();
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Success", "Default address updated"));
        } catch (IllegalArgumentException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage()));
        }
    }

    private void clearForm() {
        street = null; number = null; complement = null;
        neighborhood = null; city = null; state = null;
        postalCode = null; label = null; setAsDefault = false;
    }

    public List<Address> getAddresses() { return addresses; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getComplement() { return complement; }
    public void setComplement(String complement) { this.complement = complement; }
    public String getNeighborhood() { return neighborhood; }
    public void setNeighborhood(String neighborhood) { this.neighborhood = neighborhood; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public boolean isSetAsDefault() { return setAsDefault; }
    public void setSetAsDefault(boolean setAsDefault) { this.setAsDefault = setAsDefault; }
}
