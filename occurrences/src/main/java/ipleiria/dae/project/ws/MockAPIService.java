package ipleiria.dae.project.ws;

import ipleiria.dae.project.ejbs.MockAPIBean;
import ipleiria.dae.project.exceptions.MyEntityNotFoundException;
import org.json.JSONArray;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;

@Path("mock")
@Consumes({"application/json"})
@Produces({"application/json"})
public class MockAPIService {
    @EJB
    private MockAPIBean mockAPIBean;

    @GET
    @Path("/{resource}")
    public Response getMockData(@PathParam("resource") String resource) throws MyEntityNotFoundException {
        JSONArray jsonArray = mockAPIBean.getDataAPI(resource, "");
        return Response.ok(jsonArray.toString()).build();
    }

    @GET
    @Path("/{resource}/{code}")
    public Response getMockDataByCode(@PathParam("code") String code, @PathParam("resource") String resource) throws MyEntityNotFoundException {
        JSONArray jsonArray = mockAPIBean.getDataAPI(resource, code);
        return Response.ok(jsonArray.toString()).build();
    }


}